-- ============================================================
-- Migration 002 — Grace period + Webhook idempotency
-- Aplicar em bancos que já existiam antes desta migration.
-- Novos deploys (fresh) já têm tudo no initial_schema.sql.
-- ============================================================
-- Execute manualmente ou via entrypoint:
--   psql $DATABASE_URL -f migrations/002_billing_grace_idempotency.sql
-- ============================================================

SET search_path TO koalla;

-- 1. Grace period na tabela subscriptions
ALTER TABLE subscriptions
    ADD COLUMN IF NOT EXISTS grace_expires_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_sub_grace
    ON subscriptions(status, grace_expires_at)
    WHERE grace_expires_at IS NOT NULL;

-- 2. Tabela de idempotência para webhooks
CREATE TABLE IF NOT EXISTS webhook_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id VARCHAR(200) UNIQUE NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_webhook_events_id ON webhook_events(event_id);
