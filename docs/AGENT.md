# Koalla AI Agent Instructions

This document provides context for AI coding agents working with the koalla-core codebase.

## Quick Navigation

```
koalla-core/
├── src/main/kotlin/ai/koalla/core/
│   ├── agent/           # LLM Agent (KoallaAgent.kt)
│   ├── client/          # External API clients (Chatwoot, Asaas)
│   ├── config/          # Spring configuration and properties
│   ├── controller/      # REST API endpoints
│   ├── dto/             # Data transfer objects
│   ├── entity/          # JPA entities (database models)
│   ├── repository/      # Spring Data JPA repositories
│   ├── service/         # Business logic services
│   └── tools/           # Spring AI function calling tools
├── docs/                # This documentation
├── migrations/          # SQL migration scripts
└── build.gradle.kts     # Dependencies and build config
```

## Key Files to Understand

| Purpose | File |
|---------|------|
| Main entry point | `KoallaApplication.kt` |
| Agent with function calling | `agent/KoallaAgent.kt` |
| Message processing pipeline | `service/MessagePipelineService.kt` |
| AI Tools (8 functions) | `tools/*.kt` |
| Chatwoot HTTP client | `client/ChatwootClient.kt` |
| Configuration | `config/KoallaConfig.kt` |
| Webhook controller | `controller/WebhookController.kt` |

## Critical Flows

### 1. Webhook Processing Flow
```
POST /webhook/chatwoot
    → WebhookController.handleChatwoot()
    → MessagePipelineService.processWebhook()
        → Validate message type (only "incoming")
        → Check blocked labels
        → Validate user status
        → Transcribe audio if needed (AudioService)
        → Debounce + Queue messages
        → Lock conversation
        → Run KoallaAgent with function calling
        → Format response for WhatsApp
        → Send via ChatwootClient
        → Unlock conversation
```

### 2. Agent Function Calling Flow
```
KoallaAgent.runAgent()
    → Load chat history from database
    → Build system prompt with current date
    → Create ChatClient with 8 tools registered
    → Call OpenAI with function calling enabled
    → Tools execute via ToolContextHolder
    → Save messages to history
    → Return formatted response
```

## Available Tools (Spring AI Functions)

| Tool | File | Description |
|------|------|-------------|
| `registerTransaction` | `TransactionTools.kt` | Register income/expense |
| `listTransactions` | `TransactionTools.kt` | List by period |
| `monthlySummary` | `TransactionTools.kt` | Monthly category breakdown |
| `sendText` | `ChatwootTools.kt` | Send text message |
| `reactToMessage` | `ChatwootTools.kt` | Send emoji reaction |
| `setResponsePreference` | `ChatwootTools.kt` | Save audio/text preference |
| `sendCancellationAlert` | `ChatwootTools.kt` | Alert manager on cancellation |
| `escalateToHuman` | `EscalationTools.kt` | Hand off to human agent |

## Database Schema

All tables live in the `koalla` PostgreSQL schema. Key tables:
- `users` — User accounts linked by WhatsApp ID
- `transactions` — Financial movements (CASH_IN/CASH_OUT)
- `categories` — Transaction categories
- `chat_history` — Conversation memory per session
- `message_queue` — Debounce queue for incoming messages
- `conversation_status` — Lock state per conversation

## Configuration

Environment variables (see `application.yml`):
- `OPENAI_API_KEY` — OpenAI API key
- `CHATWOOT_BASE_URL` — Chatwoot instance URL
- `CHATWOOT_API_KEY` — Chatwoot API access token
- `DATABASE_URL` — PostgreSQL connection string

## Testing

```bash
# Run tests
./gradlew test

# Run with test profile
./gradlew bootRun --args='--spring.profiles.active=test'
```

## Common Tasks

### Adding a new tool
1. Create function in appropriate `*Tools.kt` config class
2. Add `@Bean` and `@Description` annotations
3. Register function name in `KoallaAgent.defaultFunctions()`
4. Update system prompt with tool description

### Modifying the agent prompt
Edit `SYSTEM_PROMPT` in `agent/KoallaAgent.kt`

### Adding a new entity
1. Create entity in `entity/` with `@Entity` annotation
2. Create repository interface in `repository/`
3. Add migration SQL in `migrations/`

