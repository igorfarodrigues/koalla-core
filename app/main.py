import logging
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from contextlib import asynccontextmanager
from sqlalchemy import text
from apscheduler.schedulers.asyncio import AsyncIOScheduler

from app.routers import webhook, transactions, users, auth, billing
from app.database import engine, Base, AsyncSessionLocal
from app.services import billing_service

logger = logging.getLogger(__name__)
scheduler = AsyncIOScheduler(timezone="UTC")


async def _run_expire_grace_periods() -> None:
    """Wrapper para rodar expire_grace_periods com uma sessão própria."""
    async with AsyncSessionLocal() as db:
        count = await billing_service.expire_grace_periods(db)
        if count:
            logger.info("Grace periods expirados: %d usuário(s) desativado(s)", count)


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup: create tables if needed (use Alembic in production)
    async with engine.begin() as conn:
        await conn.execute(text('CREATE SCHEMA IF NOT EXISTS koalla'))
        await conn.execute(text('CREATE EXTENSION IF NOT EXISTS "pgcrypto"'))
        await conn.run_sync(Base.metadata.create_all)

    # Inicia o scheduler — verifica grace periods expirados a cada hora
    scheduler.add_job(_run_expire_grace_periods, "interval", hours=1, id="expire_grace_periods")
    scheduler.start()

    yield

    # Shutdown
    scheduler.shutdown(wait=False)
    await engine.dispose()


app = FastAPI(
    title="Koalla Core API",
    version="0.2.2",
    lifespan=lifespan,
)

#Permite que qualquer frontend na internet faça requisções na API.
"""
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)
"""

app.add_middleware(
    CORSMiddleware,
    allow_origins=[
        "https://koalla.ai",
        "https://www.koalla.ai",
    ],
    allow_methods=["GET", "POST", "PATCH", "DELETE", "OPTIONS"],
    allow_headers=["Content-Type", "Authorization"],
)

app.include_router(webhook.router, prefix="/webhook", tags=["webhook"])
app.include_router(transactions.router, prefix="/transactions", tags=["transactions"])
app.include_router(users.router, prefix="/users", tags=["users"])
app.include_router(auth.router, prefix="/auth", tags=["auth"])
app.include_router(billing.router, prefix="/webhook", tags=["billing"])


@app.get("/health")
async def health():
    return {"status": "ok", "version": "0.2.2"}
