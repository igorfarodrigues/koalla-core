from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from uuid import UUID

from legacy.app.database import get_db
from legacy.app.models.transaction import Transaction
from legacy.app.schemas.transaction import TransactionCreate, TransactionRead

router = APIRouter()


@router.post("/", response_model=TransactionRead)
async def create_transaction(
    payload: TransactionCreate,
    db: AsyncSession = Depends(get_db),
) -> Transaction:
    tx = Transaction(**payload.model_dump())
    db.add(tx)
    await db.commit()
    await db.refresh(tx)
    return tx


@router.get("/user/{user_id}", response_model=list[TransactionRead])
async def list_user_transactions(
    user_id: UUID,
    limit: int = 50,
    db: AsyncSession = Depends(get_db),
) -> list[Transaction]:
    result = await db.execute(
        select(Transaction)
        .where(Transaction.user_id == user_id)
        .order_by(Transaction.occurred_at.desc())
        .limit(limit)
    )
    return list(result.scalars().all())


@router.delete("/{transaction_id}")
async def delete_transaction(
    transaction_id: UUID,
    db: AsyncSession = Depends(get_db),
) -> dict:
    result = await db.execute(
        select(Transaction).where(Transaction.id == transaction_id)
    )
    tx = result.scalars().first()
    if not tx:
        raise HTTPException(status_code=404, detail="Transaction not found")
    await db.delete(tx)
    await db.commit()
    return {"deleted": str(transaction_id)}
