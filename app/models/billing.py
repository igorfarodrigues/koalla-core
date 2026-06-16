import uuid
from datetime import datetime, date, timezone
from decimal import Decimal
from sqlalchemy import String, DateTime, Date, ForeignKey, Enum as SAEnum, Numeric, Text
from sqlalchemy.orm import Mapped, mapped_column
from app.database import Base
import enum


def utcnow():
    return datetime.now(timezone.utc)


class SubStatus(str, enum.Enum):
    TRIALING = "TRIALING"
    ACTIVE = "ACTIVE"
    PAST_DUE = "PAST_DUE"
    CANCELED = "CANCELED"
    EXPIRED = "EXPIRED"


class AsaasCustomer(Base):
    __tablename__ = "asaas_customers"
    __table_args__ = {"schema": "koalla"}

    user_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("koalla.users.id", ondelete="CASCADE"), primary_key=True
    )
    asaas_customer_id: Mapped[str] = mapped_column(String(50), unique=True, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)


class Subscription(Base):
    __tablename__ = "subscriptions"
    __table_args__ = {"schema": "koalla"}

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    user_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("koalla.users.id", ondelete="CASCADE"), nullable=False
    )
    asaas_subscription_id: Mapped[str | None] = mapped_column(String(50), unique=True)
    status: Mapped[SubStatus] = mapped_column(
        SAEnum(SubStatus, schema="koalla"), default=SubStatus.TRIALING
    )
    plan_name: Mapped[str | None] = mapped_column(String(50))
    next_due_date: Mapped[date | None] = mapped_column(Date)
    # Grace period: usuário permanece ativo até esta data mesmo com pagamento atrasado
    grace_expires_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)


class Invoice(Base):
    __tablename__ = "invoices"
    __table_args__ = {"schema": "koalla"}

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    user_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("koalla.users.id", ondelete="CASCADE"), nullable=False
    )
    subscription_id: Mapped[uuid.UUID | None] = mapped_column(
        ForeignKey("koalla.subscriptions.id", ondelete="SET NULL")
    )
    asaas_payment_id: Mapped[str | None] = mapped_column(String(50), unique=True)
    amount: Mapped[Decimal] = mapped_column(Numeric(12, 2), nullable=False)
    status: Mapped[str | None] = mapped_column(String(30))
    pix_code: Mapped[str | None] = mapped_column(Text)
    payment_link: Mapped[str | None] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)


class WebhookEvent(Base):
    """
    Registro de eventos de webhook já processados — garante idempotência.
    O Asaas usa entrega "at least once": o mesmo evento pode chegar mais de uma vez.
    """
    __tablename__ = "webhook_events"
    __table_args__ = {"schema": "koalla"}

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    event_id: Mapped[str] = mapped_column(String(200), unique=True, nullable=False)
    event_type: Mapped[str] = mapped_column(String(100), nullable=False)
    processed_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)
