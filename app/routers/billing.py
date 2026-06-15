"""
POST /webhook/asaas — recebe eventos de billing do Asaas.

Eventos tratados:
  PAYMENT_CONFIRMED   → ativa user, marca invoice como paga
  PAYMENT_RECEIVED    → idem (PIX usa esse evento)
  PAYMENT_OVERDUE     → marca PAST_DUE (sem desativar imediatamente)
  SUBSCRIPTION_DELETED → cancela subscription + desativa user
"""
import hmac
import hashlib

from fastapi import APIRouter, Request, Depends, HTTPException

from app.config import get_settings
from app.database import get_db
from app.services import billing_service
from sqlalchemy.ext.asyncio import AsyncSession

router = APIRouter()
settings = get_settings()

# Eventos que confirmam pagamento
PAYMENT_OK_EVENTS = {"PAYMENT_CONFIRMED", "PAYMENT_RECEIVED"}


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
    payment = data.get("payment", {})

    payment_id: str = payment.get("id", "")
    subscription_id: str | None = payment.get("subscription")

    # ── PAYMENT_CONFIRMED / PAYMENT_RECEIVED ──────────────────────────────
    if event in PAYMENT_OK_EVENTS:
        await billing_service.handle_payment_confirmed(db, payment_id, subscription_id)
        return {"status": "ok", "event": event}

    # ── PAYMENT_OVERDUE ───────────────────────────────────────────────────
    if event == "PAYMENT_OVERDUE":
        await billing_service.handle_payment_overdue(db, payment_id, subscription_id)
        return {"status": "ok", "event": event}

    # ── SUBSCRIPTION_DELETED ──────────────────────────────────────────────
    if event == "SUBSCRIPTION_DELETED":
        sub_id = data.get("subscription", {}).get("id") or subscription_id
        if sub_id:
            await billing_service.handle_subscription_deleted(db, sub_id)
        return {"status": "ok", "event": event}

    # Evento não tratado — retorna 200 para o Asaas não retentar
    return {"status": "ignored", "event": event}
