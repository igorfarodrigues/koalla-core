# Arquitetura - Visão Geral

## Objetivo

API backend do Koalla, assistente financeira pessoal via WhatsApp. Recebe webhooks do Chatwoot, processa mensagens com um agente LLM e registra transações financeiras no PostgreSQL.

## Stack Tecnológico

| Componente | Tecnologia | Versão |
|------------|------------|--------|
| Linguagem | Kotlin | 2.x |
| Framework | Spring Boot | 4.x |
| AI/LLM | Spring AI + OpenAI | 2.0 |
| Database | PostgreSQL | 15+ |
| ORM | Spring Data JPA | - |
| HTTP Client | WebFlux WebClient | - |
| Build | Gradle Kotlin DSL | 8.x |
| Runtime | JDK | 21 |

## Arquitetura de Camadas

```
┌─────────────────────────────────────────────────────────────┐
│                      Controllers                             │
│  (REST endpoints, webhook receivers)                        │
├─────────────────────────────────────────────────────────────┤
│                       Services                               │
│  (Business logic, orchestration)                            │
├─────────────────────────────────────────────────────────────┤
│                        Agent                                 │
│  (LLM interaction, function calling)                        │
├─────────────────────────────────────────────────────────────┤
│                        Tools                                 │
│  (Spring AI functions for agent)                            │
├─────────────────────────────────────────────────────────────┤
│                       Clients                                │
│  (External API integrations)                                │
├─────────────────────────────────────────────────────────────┤
│                    Repositories                              │
│  (Data access layer)                                        │
├─────────────────────────────────────────────────────────────┤
│                      Entities                                │
│  (JPA domain models)                                        │
└─────────────────────────────────────────────────────────────┘
```

## Componentes Principais

### 1. Controllers (`controller/`)
- **WebhookController**: Recebe webhooks do Chatwoot
- **UserController**: CRUD de usuários
- **TransactionController**: CRUD de transações
- **HealthController**: Health check e status

### 2. Services (`service/`)
- **MessagePipelineService**: Pipeline completo de processamento
- **TransactionService**: Lógica de transações financeiras
- **BillingService**: Gestão de cobrança e assinaturas
- **AudioService**: Transcrição de áudio via Whisper

### 3. Agent (`agent/`)
- **KoallaAgent**: Agente LLM com Spring AI function calling

### 4. Tools (`tools/`)
- **TransactionTools**: register, list, summary
- **ChatwootTools**: send_text, react, preferences, alerts
- **EscalationTools**: escalate_to_human
- **ToolContextHolder**: Contexto thread-safe para tools

### 5. Clients (`client/`)
- **ChatwootClient**: API Chatwoot (mensagens, contatos, labels)
- **AsaasClient**: API Asaas (cobranças PIX)

### 6. Entities (`entity/`)
- User, Transaction, Category, ChatHistory, MessageQueue, ConversationStatus

## Integrações Externas

```
┌──────────────┐     Webhook      ┌──────────────┐
│   Chatwoot   │ ──────────────▶  │  koalla-core │
│  (WhatsApp)  │ ◀──────────────  │              │
└──────────────┘    Messages      └──────────────┘
                                         │
                                         │ API Calls
                                         ▼
                    ┌──────────────────────────────┐
                    │         OpenAI API           │
                    │  (GPT-4, Whisper)            │
                    └──────────────────────────────┘
                                         │
                    ┌──────────────────────────────┐
                    │         Asaas API            │
                    │  (PIX, Cobranças)            │
                    └──────────────────────────────┘
```

## Modelo de Dados

Ver [data-model.md](./data-model.md) para detalhes do schema.

## Padrões Utilizados

- **Repository Pattern**: Acesso a dados via Spring Data JPA
- **Service Layer**: Lógica de negócio isolada
- **Function Calling**: Tools como beans Spring AI
- **Thread-Local Context**: Contexto do agente durante execução
- **Async Processing**: Webhooks processados assincronamente
- **Debounce Pattern**: Agregação de mensagens rápidas
- **Lock Pattern**: Evita respostas duplicadas por conversa

