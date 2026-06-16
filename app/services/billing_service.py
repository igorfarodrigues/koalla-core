"""
Billing service — orquestra o fluxo de signup com trial:
  1. Cria/recupera customer no Asaas
  2. Cria subscription recorrente com trialEndDate
  3. Persiste AsaasCustomer + Subscription + Invoice no banco
  4. Ativa user (is_active=True, plan_type=plan, status=TRIALING)

Grace period:
  Quando um pagamento atrasa, o usuário recebe 48h antes de ser desativado.
  O job `expire_grace_periods` roda a cada hora verificando assinaturas expiradas.
"""
import logging
import uuid
from datetime import date, timedelta, datetime, timezone

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import get_settings
from app.models.billing import AsaasCustomer, Subscription, Invoice, SubStatus, WebhookEvent
from app.models.user import User
from app.services import asaas_client
from app.services import chatwoot_client

logger = logging.getLogger(__name__)
settings = get_settings()

GRACE_HOURS: int = settings.GRACE_HOURS  # configurável via .env (padrão 48h)


async def start_trial(
    db: AsyncSession,
    user: User,
    plan: str,
    card_data: dict,
    card_holder_info: dict,
) -> dict:
    """
    Ponto de entrada chamado pelo /auth/signup.

    Retorna:
        {
            "subscription_id": str,
            "trial_end_date": str (YYYY-MM-DD),
            "plan": str,
        }

    Lança httpx.HTTPStatusError se o Asaas rejeitar o cartão.
    """
    plan = plan.upper()

    # ── 1. Asaas customer ─────────────────────────────────────────────────
    # Verifica se já existe registro local
    existing = await db.execute(
        select(AsaasCustomer).where(AsaasCustomer.user_id == user.id)
    )
    ac = existing.scalars().first()

    if ac:
        asaas_customer_id = ac.asaas_customer_id
    else:
        customer = await asaas_client.get_or_create_customer(
            name=user.full_name or "",
            phone=user.wa_id,
            email=user.email,
        )
        asaas_customer_id = customer["id"]
        db.add(AsaasCustomer(user_id=user.id, asaas_customer_id=asaas_customer_id))
        await db.flush()

    # ── 2. Asaas subscription ─────────────────────────────────────────────
    subscription_data = await asaas_client.create_subscription(
        customer_id=asaas_customer_id,
        plan=plan,
        card_data=card_data,
        card_holder_info=card_holder_info,
        trial_days=settings.TRIAL_DAYS,
    )

    asaas_subscription_id = subscription_data["id"]
    trial_end = date.today() + timedelta(days=settings.TRIAL_DAYS)

    # ── 3. Persist subscription ───────────────────────────────────────────
    sub = Subscription(
        user_id=user.id,
        asaas_subscription_id=asaas_subscription_id,
        status=SubStatus.TRIALING,
        plan_name=plan,
        next_due_date=trial_end,
    )
    db.add(sub)
    await db.flush()

    # ── 4. Activate user ──────────────────────────────────────────────────
    user.is_active = True
    user.plan_type = plan
    db.add(user)
    await db.commit()

    return {
        "subscription_id": asaas_subscription_id,
        "trial_end_date": trial_end.isoformat(),
        "plan": plan,
    }


async def handle_payment_confirmed(db: AsyncSession, payment_id: str, subscription_id: str | None) -> None:
    """
    Webhook PAYMENT_CONFIRMED / PAYMENT_RECEIVED:
    - Registra invoice como paga
    - Garante user ativo
    """
    if not subscription_id:
        return

    sub_result = await db.execute(
        select(Subscription).where(Subscription.asaas_subscription_id == subscription_id)
    )
    sub = sub_result.scalars().first()
    if not sub:
        return

    # Upsert invoice
    inv_result = await db.execute(
        select(Invoice).where(Invoice.asaas_payment_id == payment_id)
    )
    inv = inv_result.scalars().first()
    if not inv:
        inv = Invoice(
            user_id=sub.user_id,
            subscription_id=sub.id,
            asaas_payment_id=payment_id,
            amount=asaas_client.PLAN_VALUES.get(sub.plan_name or "", 0),
            status="CONFIRMED",
        )
        db.add(inv)
    else:
        inv.status = "CONFIRMED"

    # Ensure user is active and subscription ACTIVE
    sub.status = SubStatus.ACTIVE
    user_result = await db.execute(select(User).where(User.id == sub.user_id))
    user = user_result.scalars().first()
    if user:
        user.is_active = True

    await db.commit()


async def handle_payment_overdue(db: AsyncSession, payment_id: str, subscription_id: str | None) -> None:
    """
    Webhook PAYMENT_OVERDUE / PAYMENT_CREDIT_CARD_CAPTURE_REFUSED:
    - Upsert invoice como OVERDUE (cria se não existir)
    - Marca subscription como PAST_DUE com grace_expires_at = agora + 48h
    - Notifica o usuário via WhatsApp
    - NÃO desativa imediatamente — o job expire_grace_periods faz isso
    """
    if not subscription_id:
        return

    sub_result = await db.execute(
        select(Subscription).where(Subscription.asaas_subscription_id == subscription_id)
    )
    sub = sub_result.scalars().first()
    if not sub:
        return

    # Define grace period apenas se ainda não estiver em PAST_DUE (evita resetar o timer)
    if sub.status != SubStatus.PAST_DUE:
        sub.status = SubStatus.PAST_DUE
        sub.grace_expires_at = datetime.now(timezone.utc) + timedelta(hours=GRACE_HOURS)

        # Notifica o usuário via WhatsApp
        user_result = await db.execute(select(User).where(User.id == sub.user_id))
        user = user_result.scalars().first()
        if user:
            await _notify_payment_overdue(user.wa_id, GRACE_HOURS)

    inv_result = await db.execute(
        select(Invoice).where(Invoice.asaas_payment_id == payment_id)
    )
    inv = inv_result.scalars().first()
    if inv:
        inv.status = "OVERDUE"
    elif payment_id:
        # Pagamento ainda não existia localmente — cria o registro
        inv = Invoice(
            user_id=sub.user_id,
            subscription_id=sub.id,
            asaas_payment_id=payment_id,
            amount=asaas_client.PLAN_VALUES.get(sub.plan_name or "", 0),
            status="OVERDUE",
        )
        db.add(inv)

    await db.commit()


async def _notify_payment_overdue(wa_id: str, grace_hours: int) -> None:
    """Envia mensagem de pagamento atrasado via WhatsApp."""
    message = (
        f"⚠️ *Koalla — Pagamento pendente*\n\n"
        f"Identificamos um problema com a cobrança da sua assinatura.\n\n"
        f"Você tem *{grace_hours} horas* para regularizar antes que o acesso seja suspenso.\n\n"
        f"Se precisar de ajuda, é só responder aqui. 🐨"
    )
    try:
        await chatwoot_client.send_message_to_phone(wa_id, message)
    except Exception as exc:
        logger.warning("Falha ao notificar usuário %s sobre pagamento atrasado: %s", wa_id, exc)


async def expire_grace_periods(db: AsyncSession) -> int:
    """
    Job periódico — desativa usuários cujo grace period expirou.
    Chamado pelo APScheduler a cada hora.
    Retorna o número de usuários desativados.
    """
    now = datetime.now(timezone.utc)
    result = await db.execute(
        select(Subscription).where(
            Subscription.status == SubStatus.PAST_DUE,
            Subscription.grace_expires_at <= now,
        )
    )
    expired_subs = result.scalars().all()

    deactivated = 0
    for sub in expired_subs:
        sub.status = SubStatus.CANCELED

        user_result = await db.execute(select(User).where(User.id == sub.user_id))
        user = user_result.scalars().first()
        if user and user.is_active:
            user.is_active = False
            deactivated += 1
            logger.info("Usuário %s desativado por grace period expirado", user.wa_id)
            # Notifica encerramento
            await _notify_access_revoked(user.wa_id)

    if expired_subs:
        await db.commit()

    return deactivated


async def _notify_access_revoked(wa_id: str) -> None:
    """Notifica o usuário que o acesso foi suspenso."""
    message = (
        "🔒 *Koalla — Acesso suspenso*\n\n"
        "Infelizmente não conseguimos processar o pagamento da sua assinatura "
        "e seu acesso foi suspenso.\n\n"
        "Para reativar, entre em contato com o suporte. 🐨"
    )
    try:
        await chatwoot_client.send_message_to_phone(wa_id, message)
    except Exception as exc:
        logger.warning("Falha ao notificar usuário %s sobre suspensão: %s", wa_id, exc)


async def handle_subscription_deleted(db: AsyncSession, subscription_id: str) -> None:
    """
    Webhook SUBSCRIPTION_DELETED / SUBSCRIPTION_INACTIVATED:
    - Marca subscription como CANCELED
    - Desativa user
    """
    sub_result = await db.execute(
        select(Subscription).where(Subscription.asaas_subscription_id == subscription_id)
    )
    sub = sub_result.scalars().first()
    if not sub:
        return

    sub.status = SubStatus.CANCELED

    user_result = await db.execute(select(User).where(User.id == sub.user_id))
    user = user_result.scalars().first()
    if user:
        user.is_active = False

    await db.commit()


# ── Idempotência ───────────────────────────────────────────────────────────

async def is_event_already_processed(db: AsyncSession, event_id: str) -> bool:
    """
    Verifica se o event_id já foi processado.
    O Asaas envia webhooks com entrega "at least once" — duplicatas devem ser ignoradas.
    """
    result = await db.execute(
        select(WebhookEvent).where(WebhookEvent.event_id == event_id)
    )
    return result.scalars().first() is not None


async def mark_event_processed(db: AsyncSession, event_id: str, event_type: str) -> None:
    """
    Registra o event_id como processado.
    Chamado após o processamento bem-sucedido de qualquer evento.
    """
    db.add(WebhookEvent(event_id=event_id, event_type=event_type))
    await db.commit()
