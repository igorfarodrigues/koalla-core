from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from contextlib import asynccontextmanager
from sqlalchemy import text

from app.routers import webhook, transactions, users, auth
from app.database import engine, Base


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup: create tables if needed (use Alembic in production)
    async with engine.begin() as conn:
        await conn.execute(text('CREATE SCHEMA IF NOT EXISTS koalla'))
        await conn.execute(text('CREATE EXTENSION IF NOT EXISTS "pgcrypto"'))
        await conn.run_sync(Base.metadata.create_all)
    yield
    # Shutdown
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


@app.get("/health")
async def health():
    return {"status": "ok", "version": "0.2.2"}
