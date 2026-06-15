"""
Billing service — orquestra o fluxo de signup com trial:
  1. Cria/recupera customer no Asaas
  2. Cria subscription recorrente com trialEndDate
  3. Persiste AsaasCustomer + Subscription + Invoice no banco
  4. Ativa user (is_active=True, plan_type=plan, status=TRIALING)
"""
import uuid
from datetime import date, timedelta, datetime, timezone

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import get_settings
from app.models.billing import AsaasCustomer, Subscription, Invoice, SubStatus
from app.models.user import User
from app.services import asaas_client

settings = get_settings()


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
    Webhook PAYMENT_OVERDUE:
    - Marca invoice como vencida
    - Marca subscription como PAST_DUE
    - NÃO desativa o user imediatamente (grace period definido pelo negócio)
    """
    if not subscription_id:
        return

    sub_result = await db.execute(
        select(Subscription).where(Subscription.asaas_subscription_id == subscription_id)
    )
    sub = sub_result.scalars().first()
    if not sub:
        return

    sub.status = SubStatus.PAST_DUE

    inv_result = await db.execute(
        select(Invoice).where(Invoice.asaas_payment_id == payment_id)
    )
    inv = inv_result.scalars().first()
    if inv:
        inv.status = "OVERDUE"

    await db.commit()


async def handle_subscription_deleted(db: AsyncSession, subscription_id: str) -> None:
    """
    Webhook SUBSCRIPTION_DELETED:
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
