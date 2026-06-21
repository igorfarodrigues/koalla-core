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
├── config/                       # Configuration classes
├── entity/                       # JPA Entities
├── repository/                   # Spring Data Repositories
├── service/                      # Business Logic
├── controller/                   # REST Controllers
├── dto/                          # Data Transfer Objects
├── client/                       # External API Clients (Chatwoot, Asaas)
└── agent/                        # AI Agent (Spring AI)
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
- `GET /health` — Status da API

### Webhooks
- `POST /webhook/chatwoot` — Recebe eventos do Chatwoot
- `POST /webhook/asaas` — Recebe eventos do Asaas

### Usuários
- `GET /users/{waId}` — Busca usuário por WhatsApp ID
- `PATCH /users/{userId}/deactivate` — Desativa usuário
- `PATCH /users/{userId}/activate` — Ativa usuário

### Transações
- `POST /transactions` — Cria transação
- `GET /transactions/user/{userId}` — Lista transações do usuário
- `DELETE /transactions/{id}` — Remove transação

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

