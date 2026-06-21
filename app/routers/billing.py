"""
POST /webhook/asaas — recebe eventos de billing do Asaas.

Eventos tratados:
  PAYMENT_CONFIRMED              → ativa user, marca invoice como paga
  PAYMENT_RECEIVED               → idem (PIX usa esse evento)
  PAYMENT_OVERDUE                → marca PAST_DUE (sem desativar imediatamente)
  PAYMENT_CREDIT_CARD_CAPTURE_REFUSED → marca PAST_DUE (falha na cobrança recorrente)
  SUBSCRIPTION_DELETED           → cancela subscription + desativa user
  SUBSCRIPTION_INACTIVATED       → idem (inativação por falhas de pagamento)

Idempotência:
  Cada evento do Asaas carrega um campo "id" único (evt_...).
  Usamos a tabela webhook_events para ignorar duplicatas silenciosamente.
"""
import hmac

from fastapi import APIRouter, Request, Depends, HTTPException

from app.config import get_settings
from app.database import get_db
from app.services import billing_service
from sqlalchemy.ext.asyncio import AsyncSession

from sqlalchemy import select

from app.models.user import User

router = APIRouter()
settings = get_settings()

# Eventos que confirmam pagamento
PAYMENT_OK_EVENTS = {"PAYMENT_CONFIRMED", "PAYMENT_RECEIVED"}

# Eventos que marcam falha/atraso no pagamento
PAYMENT_FAIL_EVENTS = {"PAYMENT_OVERDUE", "PAYMENT_CREDIT_CARD_CAPTURE_REFUSED"}

# Eventos que encerram a assinatura
SUBSCRIPTION_END_EVENTS = {"SUBSCRIPTION_DELETED", "SUBSCRIPTION_INACTIVATED"}


def _validate_asaas_token(request: Request) -> None:
    """
    Valida o token enviado pelo Asaas no header 'asaas-access-token'.
    Se ASAAS_WEBHOOK_TOKEN estiver em branco, pula validação (dev/sandbox).
    """
    if not settings.ASAAS_WEBHOOK_TOKEN:
        return
    token = request.headers.get("asaas-access-token", "")
    if not hmac.compare_digest(token, settings.ASAAS_WEBHOOK_TOKEN):
        raise HTTPException(status_code=401, detail="Invalid webhook token")


@router.post("/asaas")
async def asaas_webhook(
    request: Request,
    db: AsyncSession = Depends(get_db),
) -> dict:
    """
    Recebe eventos do Asaas e atualiza o estado do usuário no banco.

    Configurar no painel Asaas:
      URL: https://api.koalla.ai/webhook/asaas
      Token: valor de ASAAS_WEBHOOK_TOKEN
    """
    _validate_asaas_token(request)

    try:
        data = await request.json()
    except Exception:
        raise HTTPException(status_code=400, detail="Invalid JSON")

    event = data.get("event", "")
    event_id: str = data.get("id", "")  # ID único do evento (ex: evt_xxx&yyy)

    # ── Idempotência ──────────────────────────────────────────────────────
    # Retorna 200 imediatamente se este evento já foi processado
    if event_id and await billing_service.is_event_already_processed(db, event_id):
        return {"status": "duplicate", "event": event}

    payment = data.get("payment", {})
    payment_id: str = payment.get("id", "")
    subscription_id: str | None = payment.get("subscription")

    # ── PAYMENT_CONFIRMED / PAYMENT_RECEIVED ──────────────────────────────
    if event in PAYMENT_OK_EVENTS:
        await billing_service.handle_payment_confirmed(db, payment_id, subscription_id)

    # ── PAYMENT_OVERDUE / PAYMENT_CREDIT_CARD_CAPTURE_REFUSED ────────────
    elif event in PAYMENT_FAIL_EVENTS:
        await billing_service.handle_payment_overdue(db, payment_id, subscription_id)

    # ── SUBSCRIPTION_DELETED / SUBSCRIPTION_INACTIVATED ──────────────────
    elif event in SUBSCRIPTION_END_EVENTS:
        sub_id = data.get("subscription", {}).get("id") or subscription_id
        if sub_id:
            await billing_service.handle_subscription_deleted(db, sub_id)

    # Marca evento como processado (independente de ser tratado ou ignorado)
    if event_id:
        await billing_service.mark_event_processed(db, event_id, event)

    return {"status": "ok", "event": event}

@router.post("/cancel-subscription/{wa_id}")
async def cancel_subscription(
    wa_id: str,
    db: AsyncSession = Depends(get_db),
) -> dict:
    """
    Cancela a assinatura do usuário.
    """

    result = await db.execute(
        select(User).where(User.wa_id == wa_id)
    )

    user = result.scalars().first()

    if not user:
        raise HTTPException(
            status_code=404,
            detail="User not found"
        )

    try:
        result = await billing_service.cancel_user_subscription(
            db=db,
            user=user,
        )

        return result

    except ValueError as exc:
        raise HTTPException(
            status_code=400,
            detail=str(exc)
        )
