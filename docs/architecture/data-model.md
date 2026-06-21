# Modelo de Dados

## Schema

Todas as tabelas residem no schema `koalla` do PostgreSQL.

## Entidades

### users
Usuários do sistema, identificados pelo número WhatsApp.

```sql
CREATE TABLE koalla.users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wa_id VARCHAR(20) UNIQUE NOT NULL,      -- WhatsApp phone number
    name VARCHAR(255),
    email VARCHAR(255),
    is_active BOOLEAN DEFAULT true,
    trial_ends_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);
```

| Campo | Tipo | Descrição |
|-------|------|-----------|
| id | UUID | Identificador único |
| wa_id | VARCHAR | Número WhatsApp (chave funcional) |
| name | VARCHAR | Nome do usuário |
| email | VARCHAR | Email opcional |
| is_active | BOOLEAN | Usuário ativo/inativo |
| trial_ends_at | TIMESTAMPTZ | Fim do período trial |

### transactions
Movimentações financeiras (receitas e despesas).

```sql
CREATE TABLE koalla.transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES koalla.users(id),
    description VARCHAR(255),
    amount BIGINT NOT NULL,                  -- Value in cents
    movement VARCHAR(20) NOT NULL,           -- CASH_IN or CASH_OUT
    category_id UUID REFERENCES koalla.categories(id),
    entity_type VARCHAR(10) DEFAULT 'PF',    -- PF or PJ
    source VARCHAR(50) DEFAULT 'whatsapp',
    occurred_at TIMESTAMPTZ DEFAULT NOW(),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);
```

| Campo | Tipo | Descrição |
|-------|------|-----------|
| amount | BIGINT | Valor em centavos (4500 = R$45,00) |
| movement | VARCHAR | `CASH_IN` (entrada) ou `CASH_OUT` (saída) |
| entity_type | VARCHAR | `PF` (pessoa física) ou `PJ` (pessoa jurídica) |
| source | VARCHAR | Origem do registro (whatsapp, api, import) |

### categories
Categorias de transações (globais e por usuário).

```sql
CREATE TABLE koalla.categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES koalla.users(id),  -- NULL = global
    name VARCHAR(100) NOT NULL,
    icon VARCHAR(10),
    created_at TIMESTAMPTZ DEFAULT NOW()
);
```

**Categorias padrão (globais):**
- Mercado, Diversao, Educação, Assinatura, Transporte
- Alimentacao, Moradia, Lazer, Saude, Investimento
- Outros, Receita

### chat_history
Histórico de conversas para memória do agente.

```sql
CREATE TABLE koalla.chat_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id VARCHAR(50) NOT NULL,         -- Usually wa_id
    message JSONB NOT NULL,                  -- {"role": "user|assistant", "content": "..."}
    created_at TIMESTAMPTZ DEFAULT NOW()
);
```

### message_queue
Fila de mensagens para debounce.

```sql
CREATE TABLE koalla.message_queue (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wa_id VARCHAR(20) NOT NULL,
    message_id VARCHAR(50) NOT NULL,
    message TEXT NOT NULL,
    timestamp TIMESTAMPTZ DEFAULT NOW()
);
```

### conversation_status
Estado de lock por conversa.

```sql
CREATE TABLE koalla.conversation_status (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id VARCHAR(50) UNIQUE NOT NULL,
    lock_conversa BOOLEAN DEFAULT false,
    aguardando_followup BOOLEAN DEFAULT false,
    numero_followup INTEGER DEFAULT 0,
    updated_at TIMESTAMPTZ DEFAULT NOW()
);
```

## Relacionamentos

```
┌──────────────────┐       ┌──────────────────┐
│      users       │───┬──▶│   transactions   │
└──────────────────┘   │   └──────────────────┘
         │             │          │
         │             │          ▼
         │             │   ┌──────────────────┐
         │             └──▶│    categories    │
         │                 └──────────────────┘
         │
         ├───────────────▶ ┌──────────────────┐
         │                 │  subscriptions   │
         │                 └──────────────────┘
         │                          │
         │                          ▼
         │                 ┌──────────────────┐
         ├───────────────▶ │     invoices     │
         │                 └──────────────────┘
         │
         └───────────────▶ ┌──────────────────┐
                           │ asaas_customers  │
                           └──────────────────┘
```

## Índices Recomendados

```sql
-- Performance de busca por usuário
CREATE INDEX idx_transactions_user_id ON koalla.transactions(user_id);
CREATE INDEX idx_transactions_occurred_at ON koalla.transactions(occurred_at);

-- Busca de histórico por sessão
CREATE INDEX idx_chat_history_session_id ON koalla.chat_history(session_id);

-- Debounce queue
CREATE INDEX idx_message_queue_wa_id ON koalla.message_queue(wa_id);
```

## Migrations

Os scripts de migração estão em `/migrations/`:
- `initial_schema.sql` — Schema inicial completo
- `002_billing_grace_idempotency.sql` — Grace period e idempotência de webhooks

## Entidades de Billing

### asaas_customers
Mapeamento entre usuários e clientes Asaas.

```sql
CREATE TABLE koalla.asaas_customers (
    user_id UUID PRIMARY KEY,
    asaas_customer_id VARCHAR(50) UNIQUE NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
```

### subscriptions
Assinaturas dos usuários.

```sql
CREATE TABLE koalla.subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    asaas_subscription_id VARCHAR(50) UNIQUE,
    status VARCHAR(20) DEFAULT 'TRIALING',  -- TRIALING, ACTIVE, PAST_DUE, CANCELED, EXPIRED
    plan_name VARCHAR(50),
    next_due_date DATE,
    grace_expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
```

| Campo | Tipo | Descrição |
|-------|------|-----------|
| status | VARCHAR | TRIALING, ACTIVE, PAST_DUE, CANCELED, EXPIRED |
| grace_expires_at | TIMESTAMPTZ | Data limite do período de carência |

### invoices
Faturas e pagamentos.

```sql
CREATE TABLE koalla.invoices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    subscription_id UUID,
    asaas_payment_id VARCHAR(50) UNIQUE,
    amount DECIMAL(12,2) NOT NULL,
    status VARCHAR(30),
    pix_code TEXT,
    payment_link TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
```

### webhook_events
Idempotência para webhooks do Asaas.

```sql
CREATE TABLE koalla.webhook_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id VARCHAR(200) UNIQUE NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ DEFAULT NOW()
);
```

