from pydantic import BaseModel, field_validator
from datetime import datetime
from uuid import UUID
from app.models.transaction import MovementType, EntityContext


class TransactionCreate(BaseModel):
    user_id: UUID
    description: str | None = None
    amount: int  # in cents
    movement: MovementType
    category_id: int | None = None
    entity_type: EntityContext = EntityContext.PF
    source: str = "whatsapp"
    occurred_at: datetime | None = None


class TransactionRead(TransactionCreate):
    id: UUID
    created_at: datetime
    updated_at: datetime

    class Config:
        from_attributes = True


class TransactionSummary(BaseModel):
    total_cash_in: int
    total_cash_out: int
    balance: int
    by_category: dict[str, int]
