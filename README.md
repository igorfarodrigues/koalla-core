# Koalla Core API

Backend da assistente financeira Koalla via WhatsApp.

## Stack

- **Kotlin 2.3** + **Spring Boot 4.1**
- **Spring AI** (OpenAI integration)
- **Spring Data JPA** + PostgreSQL
- **PGVector** para embeddings (opcional)

## Estrutura

```
src/main/kotlin/ai/koalla/core/
├── KoallaApplication.kt          # Entry point
├── agent/                        # AI Agent (Spring AI - KoallaAgent)
├── client/                       # External API Clients (Chatwoot, Asaas)
├── config/                       # Configuration classes
├── controller/                   # REST Controllers
├── domain/                       # Domain models (User, Transaction, AgentContext)
├── dto/                          # Data Transfer Objects
├── entity/                       # JPA Entities
├── exception/                    # Exception handling (GlobalExceptionHandler)
├── gateway/                      # API Gateways (Asaas, Chatwoot)
├── mapper/                       # Entity/Domain mappers
├── observability/                # Metrics and monitoring
├── repository/                   # Spring Data Repositories
├── service/                      # Business Logic
├── tools/                        # Spring AI function calling tools
└── util/                         # Utilities (WebhookSecurity)
```

## Configuração

Crie um arquivo `.env` na raiz do projeto:

```bash
# Database
DATABASE_URL=jdbc:postgresql://localhost:5432/koalla
DB_USERNAME=koalla
DB_PASSWORD=koalla

# OpenAI
OPENAI_API_KEY=sk-...

# Chatwoot
CHATWOOT_API_TOKEN=...
CHATWOOT_WEBHOOK_SECRET=...
CHATWOOT_ACCOUNT_ID=1

# Asaas
ASAAS_API_KEY=...
ASAAS_WEBHOOK_TOKEN=...
```

## Execução Local

### Com Docker Compose

```bash
docker-compose up -d
```

### Com Gradle

```bash
./gradlew bootRun
```

## API Endpoints

### Health
- `GET /actuator/health` — Status da API

### Webhooks
- `POST /webhook/chatwoot` — Recebe eventos do Chatwoot
- `POST /webhook/asaas` — Recebe eventos do Asaas (billing)

### Usuários
- `GET /api/users/{waId}` — Busca usuário por WhatsApp ID
- `PATCH /api/users/{userId}/deactivate` — Desativa usuário
- `PATCH /api/users/{userId}/activate` — Ativa usuário

### Transações
- `POST /api/transactions` — Cria transação
- `GET /api/transactions/user/{userId}` — Lista transações do usuário
- `GET /api/transactions/user/{userId}/summary` — Resumo mensal
- `DELETE /api/transactions/{id}` — Remove transação

### Auth
- `POST /auth/signup` — Cadastro com trial
- `GET /auth/status/{waId}` — Status do cadastro

## Migração do Legacy

Este projeto é uma migração do código Python/FastAPI localizado em `legacy/`.
A documentação original está em `legacy/docs/`.

### Equivalências

| Python (Legacy)         | Kotlin (Spring)                    |
|------------------------|-----------------------------------|
| FastAPI Router         | Spring @RestController            |
| Pydantic Schema        | Kotlin data class + validation    |
| SQLAlchemy Model       | JPA Entity                        |
| asyncpg                | HikariCP + PostgreSQL Driver      |
| LangChain              | Spring AI                         |
| httpx.AsyncClient      | WebClient                         |
| APScheduler            | @Scheduled                        |

## Build

```bash
./gradlew build
```

## Testes

```bash
./gradlew test
```

