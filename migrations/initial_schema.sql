-- ============================================================
-- Koalla - Initial Schema
-- ============================================================

CREATE SCHEMA IF NOT EXISTS koalla AUTHORIZATION koalla;
SET search_path TO koalla;

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ── ENUMS ──────────────────────────────────────────────────
CREATE TYPE movement_type AS ENUM ('CASH_IN', 'CASH_OUT');
CREATE TYPE entity_context AS ENUM ('PF', 'PJ');
CREATE TYPE sub_status AS ENUM ('TRIALING', 'ACTIVE', 'PAST_DUE', 'CANCELED', 'EXPIRED');

-- ── USERS ──────────────────────────────────────────────────
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wa_id VARCHAR(20) UNIQUE NOT NULL,
    full_name VARCHAR(100),
    email VARCHAR(100) UNIQUE,
    plan_type VARCHAR(30) DEFAULT 'FREE',
    lifetime BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    update_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- ── AUTH ───────────────────────────────────────────────────
CREATE TABLE auth (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID UNIQUE NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    magic_link_token TEXT UNIQUE,
    token_expires_at TIMESTAMP WITH TIME ZONE,
    last_login TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- ── USER STATES ────────────────────────────────────────────
CREATE TABLE user_states (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    current_state VARCHAR(50) DEFAULT 'IDLE',
    content TEXT,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- ── CATEGORIES ─────────────────────────────────────────────
CREATE TABLE categories (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    user_id UUID NULL REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE category_keywords (
    id SERIAL PRIMARY KEY,
    keyword VARCHAR(50) NOT NULL,
    category_id INT REFERENCES categories(id) ON DELETE CASCADE
);

-- ── TRANSACTIONS ───────────────────────────────────────────
CREATE TABLE transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    description TEXT,
    amount BIGINT NOT NULL,
    movement movement_type NOT NULL,
    category_id INT REFERENCES categories(id),
    entity_type entity_context NOT NULL,
    source VARCHAR(20) DEFAULT 'whatsapp',
    external_id VARCHAR(100),
    occurred_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- ── WHATSAPP HISTORY ───────────────────────────────────────
CREATE TABLE wp_message_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    direction VARCHAR(10) NOT NULL,
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- ── BILLING ────────────────────────────────────────────────
CREATE TABLE asaas_customers (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    asaas_customer_id VARCHAR(50) UNIQUE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    asaas_subscription_id VARCHAR(50) UNIQUE,
    status sub_status DEFAULT 'TRIALING',
    plan_name VARCHAR(50),
    next_due_date DATE,
    grace_expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE invoices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    subscription_id UUID REFERENCES subscriptions(id) ON DELETE SET NULL,
    asaas_payment_id VARCHAR(50) UNIQUE,
    amount DECIMAL(12,2) NOT NULL,
    status VARCHAR(30),
    pix_code TEXT,
    payment_link TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- ── INTERNAL: MESSAGE QUEUE (replaces n8n_fila_mensagens) ──
CREATE TABLE message_queue (
    id SERIAL PRIMARY KEY,
    wa_id VARCHAR(20) NOT NULL,
    message_id VARCHAR(100) NOT NULL,
    message TEXT NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- ── INTERNAL: CHAT HISTORY (replaces n8n_historico_mensagens) ──
-- LangChain-compatible table (langchain_postgres PostgresChatMessageHistory)
CREATE TABLE chat_history (
    id SERIAL PRIMARY KEY,
    session_id VARCHAR(50) NOT NULL,
    message JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- ── INTERNAL: CONVERSATION STATUS (replaces n8n_status_atendimento) ──
CREATE TABLE conversation_status (
    session_id VARCHAR(50) PRIMARY KEY,
    lock_conversa BOOLEAN DEFAULT FALSE,
    aguardando_followup BOOLEAN DEFAULT FALSE,
    numero_followup INT DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- ── WEBHOOK IDEMPOTENCY ────────────────────────────────────
-- Armazena IDs de eventos já processados para evitar duplicatas (Asaas: at-least-once)
CREATE TABLE webhook_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id VARCHAR(200) UNIQUE NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- ── INDEXES ────────────────────────────────────────────────
CREATE INDEX idx_trans_user_date ON transactions(user_id, created_at DESC);
CREATE INDEX idx_wp_history_user ON wp_message_history(user_id, created_at DESC);
CREATE INDEX idx_auth_token ON auth(magic_link_token);
CREATE INDEX idx_message_queue_waid ON message_queue(wa_id);
CREATE INDEX idx_chat_history_session ON chat_history(session_id);
CREATE INDEX idx_webhook_events_id ON webhook_events(event_id);
CREATE INDEX idx_sub_grace ON subscriptions(status, grace_expires_at) WHERE grace_expires_at IS NOT NULL;

-- ── SEED: Default categories ───────────────────────────────
INSERT INTO categories (name) VALUES
    ('Mercado'), ('Diversao'), ('Educação'), ('Assinatura'),
    ('Transporte'), ('Alimentacao'), ('Moradia'), ('Lazer'),
    ('Saude'), ('Investimento'), ('Outros'), ('Receita');
