# ── Stage 1: builder ────────────────────────────────────────────────────────
FROM python:3.12-slim AS builder

RUN apt-get update && apt-get install -y --no-install-recommends gcc libpq-dev \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir --upgrade pip \
    && pip install --no-cache-dir --prefix=/install -r requirements.txt


# ── Stage 2: runtime ────────────────────────────────────────────────────────
FROM python:3.12-slim AS runtime

# ffmpeg required by pydub for audio conversion
RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg libpq5 \
    && rm -rf /var/lib/apt/lists/*

# Non-root user for security
RUN useradd --create-home --shell /bin/bash appuser

WORKDIR /app

# Copy installed packages from builder
COPY --from=builder /install /usr/local

# Copy application source
COPY --chown=appuser:appuser .. .

USER appuser

EXPOSE 8000

# WORKERS: 2 * CPU cores + 1 is a common rule of thumb.
# Override via env var WORKERS (Coolify → Environment Variables).
ENV WORKERS=2
ENV PORT=8000

HEALTHCHECK --interval=30s --timeout=10s --start-period=15s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:${PORT}/health')"

ENTRYPOINT ["sh", "entrypoint.sh"]
