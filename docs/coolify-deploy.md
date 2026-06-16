# Deploy no Coolify — Koalla Core

## Estrutura de ambientes

| Ambiente   | Branch  | Asaas URL                        | Propósito               |
|------------|---------|----------------------------------|-------------------------|
| **Sandbox**| `dev`   | `https://api-sandbox.asaas.com`  | Testes de billing       |
| **Produção**| `main` | `https://api.asaas.com`          | Clientes reais          |

---

## O que o Coolify usa

O deploy é 100% via **Dockerfile** — o Coolify detecta automaticamente.  
Não é necessário nenhum arquivo extra de configuração de build.

```
koalla-core/
├── Dockerfile          ← Coolify usa este
├── entrypoint.sh       ← roda migrations + uvicorn
├── migrations/
│   └── initial_schema.sql   ← aplicado no startup (fresh deploy)
│   └── 002_billing_grace_idempotency.sql  ← aplicar manualmente em DB existente
└── requirements.txt
```

---

## 1. Criar o serviço no Coolify

1. **New Resource → Application → Git Repository**
2. Selecionar o repo `koalla-core`
3. **Build Pack:** `Dockerfile`
4. **Port:** `8000`
5. **Health check path:** `/health`

---

## 2. Add-on Postgres

1. No mesmo projeto → **Add Resource → Database → PostgreSQL**
2. Versão recomendada: `16`
3. Coolify gera a `DATABASE_URL` automaticamente — copie e use abaixo

---

## 3. Variáveis de Ambiente

Configure em **Application → Environment Variables**.

### 🧪 Sandbox (`dev`)

```env
# Database
DATABASE_URL=postgresql+asyncpg://<gerado_pelo_coolify>

# OpenAI
OPENAI_API_KEY=sk-...
OPENAI_MODEL=gpt-4o-mini
OPENAI_FORMATTING_MODEL=gpt-4.1-mini

# Chatwoot
CHATWOOT_URL=https://chatwoot.koalla.ai
CHATWOOT_API_TOKEN=<token_chatwoot>
CHATWOOT_ACCOUNT_ID=1
CHATWOOT_WEBHOOK_SECRET=<segredo_webhook_chatwoot>

# Asaas — SANDBOX
ASAAS_URL=https://api-sandbox.asaas.com
ASAAS_API_KEY=$aact_hmlg_...
ASAAS_WEBHOOK_TOKEN=<token_gerado_no_painel_asaas_sandbox>

# Planos e trial
TRIAL_DAYS=15
GRACE_HOURS=48
PLAN_STARTER_VALUE=29.90
PLAN_PRO_VALUE=79.90
PLAN_BUSINESS_VALUE=99.90

# Agent
MESSAGE_QUEUE_WAIT_SECONDS=2.0
AGENT_MAX_ITERATIONS=10
MEMORY_WINDOW_LENGTH=100

# Workers (2 × vCPUs + 1 é o padrão)
WORKERS=3

# WhatsApp
KOALLA_WA_NUMBER=5531936185547
ALERT_CONVERSATION_ID=

# Legacy
CHARGE_VALUE=500.0
CHARGE_DURATION_MINUTES=30
```

### 🚀 Produção (`main`)

Igual ao sandbox, trocando apenas:

```env
ASAAS_URL=https://api.asaas.com
ASAAS_API_KEY=$aact_prod_...
ASAAS_WEBHOOK_TOKEN=<token_gerado_no_painel_asaas_producao>
WORKERS=5
```

---

## 4. Configurar Webhook no painel Asaas

Após o deploy estar rodando:

1. Acesse **Painel Asaas → Integrações → Webhooks**
2. Clique em **Novo Webhook**
3. Preencha:

| Campo   | Valor                                        |
|---------|----------------------------------------------|
| URL     | `https://api.koalla.ai/webhook/asaas`        |
| Token   | (gere um token aleatório e cole aqui e no env `ASAAS_WEBHOOK_TOKEN`) |
| Eventos | ✅ Cobranças + ✅ Assinaturas                 |

4. Copie o **Token de acesso** gerado e defina no Coolify como `ASAAS_WEBHOOK_TOKEN`

> **Sandbox:** use `https://sandbox.api.koalla.ai/webhook/asaas` (ou o domínio do seu ambiente dev no Coolify)

---

## 5. Migration em banco existente

Se o banco já existia (re-deploy / update), rode manualmente:

```bash
# Conecte no container do Coolify ou via psql externo
psql $DATABASE_URL -f migrations/002_billing_grace_idempotency.sql
```

Para **fresh deploy** (primeiro deploy), o `entrypoint.sh` já aplica o `initial_schema.sql` automaticamente.

---

## 6. Whitelist de IPs do Asaas (opcional mas recomendado)

Se usar Cloudflare ou firewall, libere os IPs oficiais do Asaas:  
https://docs.asaas.com/docs/whitelist-de-ips

---

## 7. Checklist pré-go-live

- [ ] `ASAAS_URL` aponta para produção (`api.asaas.com`)
- [ ] `ASAAS_API_KEY` é a chave de **produção** (`$aact_prod_...`)
- [ ] Webhook configurado no painel Asaas de produção
- [ ] `ASAAS_WEBHOOK_TOKEN` definido e igual no Coolify e no painel Asaas
- [ ] `OPENAI_API_KEY` válida
- [ ] Postgres add-on com backup ativado no Coolify
- [ ] Health check respondendo: `GET /health` → `{"status":"ok"}`
- [ ] CORS: domínio da LP (`koalla.ai`) está na lista em `main.py`
