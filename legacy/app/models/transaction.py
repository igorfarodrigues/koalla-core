import uuid
from datetime import datetime, timezone
from sqlalchemy import String, BigInteger, Integer, DateTime, ForeignKey, Enum as SAEnum
from sqlalchemy.orm import Mapped, mapped_column
from legacy.app.database import Base
import enum


def utcnow():
    return datetime.now(timezone.utc)


class MovementType(str, enum.Enum):
    CASH_IN = "CASH_IN"
    CASH_OUT = "CASH_OUT"


class EntityContext(str, enum.Enum):
    PF = "PF"
    PJ = "PJ"


class Category(Base):
    __tablename__ = "categories"
    __table_args__ = {"schema": "koalla"}

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    name: Mapped[str] = mapped_column(String(100), nullable=False)
    user_id: Mapped[uuid.UUID | None] = mapped_column(
        ForeignKey("koalla.users.id", ondelete="CASCADE"), nullable=True
    )


class CategoryKeyword(Base):
    __tablename__ = "category_keywords"
    __table_args__ = {"schema": "koalla"}

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    keyword: Mapped[str] = mapped_column(String(50), nullable=False)
    category_id: Mapped[int] = mapped_column(
        ForeignKey("koalla.categories.id", ondelete="CASCADE")
    )


class Transaction(Base):
    __tablename__ = "transactions"
    __table_args__ = {"schema": "koalla"}

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    user_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("koalla.users.id", ondelete="CASCADE"), nullable=False
    )
    description: Mapped[str | None]
    amount: Mapped[int] = mapped_column(BigInteger, nullable=False)  # stored in cents
    movement: Mapped[MovementType] = mapped_column(
        SAEnum(MovementType, schema="koalla"), nullable=False
    )
    category_id: Mapped[int | None] = mapped_column(
        ForeignKey("koalla.categories.id")
    )
    entity_type: Mapped[EntityContext] = mapped_column(
        SAEnum(EntityContext, schema="koalla"), nullable=False, default=EntityContext.PF
    )
    source: Mapped[str] = mapped_column(String(20), default="whatsapp")
    external_id: Mapped[str | None] = mapped_column(String(100))
    occurred_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=utcnow
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=utcnow, onupdate=utcnow
    )
