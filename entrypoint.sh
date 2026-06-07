#!/bin/sh
set -e

echo "▶ koalla-core starting..."

# ── Run DB migration (idempotent) ─────────────────────────────────────────
# Only runs if DATABASE_URL is set and psql is available.
# In production (Coolify) the Postgres add-on is already running before
# this container starts, so this always succeeds on first deploy.
if [ -n "$DATABASE_URL" ]; then
  echo "▶ Applying database schema..."

  # Convert asyncpg URL to plain psql-compatible URL
  PSQL_URL=$(echo "$DATABASE_URL" | sed 's/postgresql+asyncpg/postgresql/')

  # psql is not in this image — use Python instead
  python - <<'EOF'
import os, asyncio
from sqlalchemy.ext.asyncio import create_async_engine

async def migrate():
    url = os.environ["DATABASE_URL"]
    sql = open("migrations/initial_schema.sql").read()
    engine = create_async_engine(url, echo=False)
    async with engine.begin() as conn:
        # Run each statement individually, skip errors (objects already exist)
        for stmt in sql.split(";"):
            stmt = stmt.strip()
            if not stmt or stmt.startswith("--"):
                continue
            try:
                await conn.execute(__import__("sqlalchemy").text(stmt))
            except Exception as e:
                # Ignore "already exists" errors on re-deploy
                msg = str(e).lower()
                if "already exists" in msg or "duplicate" in msg:
                    pass
                else:
                    print(f"  [migration] warning: {e}")
    await engine.dispose()
    print("  [migration] done.")

asyncio.run(migrate())
EOF
else
  echo "  [migration] DATABASE_URL not set, skipping."
fi

# ── Start API ─────────────────────────────────────────────────────────────
echo "▶ Starting uvicorn (workers=${WORKERS}, port=${PORT})..."
exec uvicorn app.main:app \
  --host 0.0.0.0 \
  --port "${PORT}" \
  --workers "${WORKERS}" \
  --loop uvloop \
  --http httptools \
  --proxy-headers \
  --forwarded-allow-ips "*"
