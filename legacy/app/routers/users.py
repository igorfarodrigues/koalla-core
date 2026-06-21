from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from uuid import UUID
from pydantic import BaseModel

from legacy.app.database import get_db
from legacy.app.models.user import User

router = APIRouter()


class UserRead(BaseModel):
    id: UUID
    wa_id: str
    full_name: str | None
    email: str | None
    plan_type: str
    is_active: bool

    class Config:
        from_attributes = True


@router.get("/{wa_id}", response_model=UserRead)
async def get_user_by_wa_id(
    wa_id: str,
    db: AsyncSession = Depends(get_db),
) -> User:
    result = await db.execute(select(User).where(User.wa_id == wa_id))
    user = result.scalars().first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    return user


@router.patch("/{user_id}/deactivate")
async def deactivate_user(
    user_id: UUID,
    db: AsyncSession = Depends(get_db),
) -> dict:
    result = await db.execute(select(User).where(User.id == user_id))
    user = result.scalars().first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    user.is_active = False
    await db.commit()
    return {"status": "deactivated"}
