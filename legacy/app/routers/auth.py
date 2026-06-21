"""
Auth endpoints:
  POST /auth/signup   — cadastro + início de trial (com cartão)
  GET  /auth/status/{wa_id} — polling de ativação pelo frontend
"""
import re

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from pydantic import BaseModel, field_validator

from legacy.app.database import get_db
from legacy.app.models.user import User, Auth
from legacy.app.config import get_settings
from legacy.app.services import billing_service

settings = get_settings()
router = APIRouter()


# ── Schemas ────────────────────────────────────────────────────────────────

class CardData(BaseModel):
    holderName: str
    number: str
    expiryMonth: str
    expiryYear: str
    ccv: str


class CardHolderInfo(BaseModel):
    name: str
    email: str
    cpfCnpj: str
    phone: str
    postalCode: str
    addressNumber: str


class SignupRequest(BaseModel):
    name: str
    phone: str
    email: str | None = None
    plan: str = "STARTER"          # STARTER | PRO | BUSINESS
    card: CardData
    cardHolder: CardHolderInfo

    @field_validator("phone")
    @classmethod
    def normalize_phone(cls, v: str) -> str:
        digits = re.sub(r"\D", "", v)
        if not digits.startswith("55"):
            digits = f"55{digits}"
        if len(digits) < 12 or len(digits) > 13:
            raise ValueError("Telefone inválido. Use o formato: (11) 99999-9999")
        return digits

    @field_validator("name")
    @classmethod
    def validate_name(cls, v: str) -> str:
        v = v.strip()
        if len(v) < 2:
            raise ValueError("Nome muito curto")
        return v

    @field_validator("plan")
    @classmethod
    def validate_plan(cls, v: str) -> str:
        valid = {"STARTER", "PRO", "BUSINESS"}
        if v.upper() not in valid:
            raise ValueError(f"Plano inválido. Escolha: {', '.join(valid)}")
        return v.upper()


class SignupResponse(BaseModel):
    success: bool
    wa_link: str
    trial_end_date: str | None = None
    plan: str | None = None
    detail: str | None = None


class StatusResponse(BaseModel):
    wa_id: str
    is_active: bool
    plan_type: str | None = None


# ── Endpoints ──────────────────────────────────────────────────────────────

@router.post("/signup", response_model=SignupResponse)
async def signup(
    body: SignupRequest,
    db: AsyncSession = Depends(get_db),
) -> SignupResponse:
    """
    Cadastro via landing page.

    Fluxo:
      1. Verifica se número já existe
      2. Cria User (is_active=False enquanto o cartão não é validado)
      3. Chama billing_service.start_trial (Asaas customer + subscription)
      4. Asaas valida cartão e cria subscription com trial
      5. billing_service ativa o user (is_active=True, status=TRIALING)
      6. Retorna wa_link para o frontend redirecionar
    """
    wa_number = settings.KOALLA_WA_NUMBER
    wa_link = (
        f"https://wa.me/{wa_number}"
        f"?text=Oi+Koalla%21+Acabei+de+me+cadastrar+%F0%9F%90%A8"
    )

    # Verifica duplicata
    result = await db.execute(select(User).where(User.wa_id == body.phone))
    existing = result.scalars().first()

    if existing:
        if existing.is_active:
            return SignupResponse(
                success=False,
                detail="already_registered",
                wa_link=f"https://wa.me/{wa_number}?text=Oi+Koalla%21",
            )
        # Usuário existe mas estava inativo — tenta reativar com novo cartão
        user = existing
    else:
        # Cria novo user (inativo até cartão ser validado)
        user = User(
            wa_id=body.phone,
            full_name=body.name,
            email=body.email,
            plan_type=body.plan,
            is_active=False,
        )
        db.add(user)
        await db.flush()

        auth = Auth(user_id=user.id)
        db.add(auth)
        await db.flush()

    # Inicia trial via Asaas
    try:
        billing = await billing_service.start_trial(
            db=db,
            user=user,
            plan=body.plan,
            card_data=body.card.model_dump(),
            card_holder_info=body.cardHolder.model_dump(),
        )
    except Exception as exc:
        # Asaas rejeitou o cartão ou erro de comunicação
        detail = str(exc)
        # Tenta extrair mensagem amigável do Asaas
        if hasattr(exc, "response"):
            try:
                err = exc.response.json()
                msgs = err.get("errors", [])
                if msgs:
                    detail = msgs[0].get("description", detail)
            except Exception:
                pass
        raise HTTPException(status_code=422, detail=detail)

    return SignupResponse(
        success=True,
        wa_link=wa_link,
        trial_end_date=billing["trial_end_date"],
        plan=billing["plan"],
    )


@router.get("/status/{wa_id}", response_model=StatusResponse)
async def get_signup_status(
    wa_id: str,
    db: AsyncSession = Depends(get_db),
) -> StatusResponse:
    """
    Polling endpoint — frontend chama a cada 5s para saber se o trial foi ativado.
    Normaliza o wa_id para garantir que tem código do país.
    """
    digits = re.sub(r"\D", "", wa_id)
    if not digits.startswith("55"):
        digits = f"55{digits}"

    result = await db.execute(select(User).where(User.wa_id == digits))
    user = result.scalars().first()

    if not user:
        raise HTTPException(status_code=404, detail="Usuário não encontrado")

    return StatusResponse(
        wa_id=user.wa_id,
        is_active=user.is_active,
        plan_type=user.plan_type,
    )
