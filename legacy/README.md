# koalla-core

API backend do Koalla, assistente financeira pessoal via WhatsApp. Recebe webhooks do Chatwoot, processa mensagens com um agente LLM e registra transações financeiras no PostgreSQL.

## Stack

- **Python 3.12** + **FastAPI** + **SQLAlchemy async**
- **PostgreSQL** (schema `koalla`)
- **LangChain** + **OpenAI** (gpt-4o-mini / Whisper)
- **Chatwoot** como canal de WhatsApp
- **Asaas** para cobrança (PIX)
- Deploy via **Docker** no **Coolify**

## Estrutura

```
app/
├── main.py                  # FastAPI app + lifespan
├── config.py                # Settings via pydantic-settings
├── database.py              # Engine async + sessão
├── models/                  # SQLAlchemy ORM
│   ├── user.py              # users, auth, user_states
│   ├── transaction.py       # transactions, categories
│   ├── message.py           # wp_message_history + tabelas internas
│   └── billing.py           # asaas_customers, subscriptions, invoices
├── schemas/
│   ├── chatwoot.py          # Payload do webhook Chatwoot
│   └── transaction.py       # DTOs de transação
├── routers/
│   ├── webhook.py           # POST /webhook/chatwoot
│   ├── transactions.py      # CRUD de transações
│   └── users.py             # Leitura de usuários
├── services/
│   ├── message_pipeline.py  # Pipeline completo (debounce → lock → agent → resposta)
│   ├── agent_service.py     # Agente Koalla (LangChain)
│   ├── chatwoot_client.py   # Cliente HTTP Chatwoot
│   ├── audio_service.py     # Transcrição via Whisper
│   └── asaas_client.py      # Cliente HTTP Asaas
└── tools/                   # Ferramentas do agente
    ├── transaction_tools.py # register_transaction, list_transactions, monthly_summary
    ├── chatwoot_tools.py    # send_text, react_to_message, set_response_preference
    └── escalation_tools.py  # escalate_to_human
migrations/
└── initial_schema.sql       # Schema completo do banco
```

## Fluxo do webhook

```
Chatwoot → POST /webhook/chatwoot
  ↓
Extrai info (telefone, mensagem, conta, conversa)
  ↓
/reset → limpa memória, fila e atributos
/teste → adiciona label testando-agente
  ↓
Filtra: só incoming, sem labels agente-off / gestor / testando-agente
  ↓
Detecta tipo: texto | arquivo | áudio (→ Whisper)
  ↓
Enfileira → aguarda 100ms → verifica se é a última mensagem (debounce)
  ↓
Verifica lock de conversa → bloqueia
  ↓
Agente Koalla (LangChain + gpt-4o-mini + memória Postgres)
  ↓
Formata saída para WhatsApp → quebra em chunks → envia via Chatwoot
  ↓
Desbloqueia conversa
```

## Setup local

```bash
cp .env.example .env
# preencha as variáveis no .env

docker compose up --build
```

> Se você ainda não criou o `.env`, o compose sobe com valores padrão seguros para desenvolvimento,
> mas integrações como OpenAI e Chatwoot continuam exigindo credenciais válidas.

A API sobe em `http://localhost:8000`. Docs em `/docs`.

## Variáveis de ambiente

| Variável | Descrição |
|---|---|
| `DATABASE_URL` | `postgresql+asyncpg://user:pass@host/db` |
| `OPENAI_API_KEY` | Chave da OpenAI |
| `OPENAI_MODEL` | Modelo do agente (default: `gpt-4o-mini`) |
| `CHATWOOT_URL` | URL da instância Chatwoot |
| `CHATWOOT_API_TOKEN` | Token de acesso da conta Chatwoot |
| `CHATWOOT_ACCOUNT_ID` | ID da conta no Chatwoot |
| `ASAAS_URL` | URL da API Asaas (sandbox ou produção) |
| `ASAAS_API_KEY` | Chave de acesso Asaas |
| `ALERT_CONVERSATION_ID` | ID da conversa para alertas de escalação |

## Deploy no Coolify

1. Crie um novo serviço do tipo **Dockerfile** apontando para este repositório
2. Configure as variáveis de ambiente acima
3. Adicione um serviço **PostgreSQL** e use a connection string gerada na variável `DATABASE_URL`
4. Configure o webhook no Chatwoot: `https://<sua-url>/webhook/chatwoot`

## Endpoints

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/health` | Health check |
| `POST` | `/webhook/chatwoot` | Recebe eventos do Chatwoot |
| `GET` | `/users/{wa_id}` | Busca usuário por número WhatsApp |
| `GET` | `/transactions/user/{user_id}` | Lista transações do usuário |
| `POST` | `/transactions/` | Cria transação manual |
| `DELETE` | `/transactions/{id}` | Remove transação |

## Guia para agentes de IA

Consulte `docs/README.md` para contexto de arquitetura, regras de negócio, engenharia/segurança e manual de testes.
