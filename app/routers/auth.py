"""
POST /auth/signup — cadastro via landing page.
Cria o usuário no banco e retorna o link do WhatsApp para iniciar o onboarding.
"""
from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from pydantic import BaseModel, field_validator
import re

from app.database import get_db
from app.models.user import User, Auth
from app.config import get_settings

settings = get_settings()
router = APIRouter()


# ── Schemas ────────────────────────────────────────────────────────────────

class SignupRequest(BaseModel):
    name: str
    phone: str
    email: str | None = None

    @field_validator("phone")
    @classmethod
    def normalize_phone(cls, v: str) -> str:
        """Remove formatação e garante código do país 55 (Brasil)."""
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


class SignupResponse(BaseModel):
    success: bool
    wa_link: str
    detail: str | None = None


# ── Endpoint ───────────────────────────────────────────────────────────────

@router.post("/signup", response_model=SignupResponse)
async def signup(
    body: SignupRequest,
    db: AsyncSession = Depends(get_db),
) -> SignupResponse:
    """
    Cadastro via landing page.

    - Se o número já existe: retorna already_registered + wa_link (para logar)
    - Se é novo: cria User (TRIAL) + registro Auth + retorna wa_link de boas-vindas
    """
    wa_number = settings.KOALLA_WA_NUMBER
    wa_link = (
        f"https://wa.me/{wa_number}"
        f"?text=Oi+Koalla%21+Acabei+de+me+cadastrar+%F0%9F%90%A8"
    )

    # Verifica se número já está cadastrado
    result = await db.execute(select(User).where(User.wa_id == body.phone))
    existing = result.scalars().first()

    if existing:
        return SignupResponse(
            success=False,
            detail="already_registered",
            wa_link=f"https://wa.me/{wa_number}?text=Oi+Koalla%21",
        )

    # Cria usuário em período de trial
    user = User(
        wa_id=body.phone,
        full_name=body.name,
        email=body.email,
        plan_type="TRIAL",
        is_active=True,
    )
    db.add(user)
    await db.flush()  # gera o UUID antes do commit

    # Cria registro de auth (pronto para magic link / PIN no futuro)
    auth = Auth(user_id=user.id)
    db.add(auth)

    await db.commit()

    return SignupResponse(success=True, wa_link=wa_link)
