# Tools Reference

## Overview

Tools são funções disponíveis para o agente LLM executar durante o processamento de mensagens. Implementadas como beans Spring com `@Description` para function calling.

## Arquitetura

```
┌─────────────────────────────────────────────────────────────┐
│                      KoallaAgent                             │
│  ChatClient.builder(chatModel)                              │
│      .defaultFunctions("registerTransaction", ...)          │
└─────────────────────────────────────────────────────────────┘
                              │
                              │ Function Call
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                   ToolContextHolder                          │
│  ThreadLocal<AgentContext>                                  │
└─────────────────────────────────────────────────────────────┘
                              │
                              │ context.require()
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                      Tool Beans                              │
│  @Bean + @Description + Function<Request, String>           │
└─────────────────────────────────────────────────────────────┘
```

## Tools Disponíveis

### 1. registerTransaction

**Arquivo:** `tools/TransactionTools.kt`

**Descrição:** Registra uma transação financeira (receita ou despesa).

**Parâmetros:**
| Nome | Tipo | Obrigatório | Descrição |
|------|------|-------------|-----------|
| description | String | Sim | Descrição curta (ex: "Uber", "Almoço") |
| amountCents | Long | Sim | Valor em centavos (4500 = R$45) |
| movement | String | Sim | "CASH_IN" ou "CASH_OUT" |
| categoryName | String | Sim | Nome da categoria |
| occurredAt | String | Não | Data ISO (YYYY-MM-DD), default: hoje |

**Exemplo de chamada pelo LLM:**
```json
{
  "name": "registerTransaction",
  "arguments": {
    "description": "Almoço",
    "amountCents": 4500,
    "movement": "CASH_OUT",
    "categoryName": "Alimentacao"
  }
}
```

**Retorno:** `"💸 Despesa de R$45.00 (Almoço) registrada em Alimentacao."`

---

### 2. listTransactions

**Arquivo:** `tools/TransactionTools.kt`

**Descrição:** Lista transações do usuário por período.

**Parâmetros:**
| Nome | Tipo | Obrigatório | Descrição |
|------|------|-------------|-----------|
| period | String | Sim | "today", "week", "month" ou "YYYY-MM" |
| category | String | Não | Filtro por categoria |

**Exemplo:**
```json
{
  "name": "listTransactions",
  "arguments": {
    "period": "month",
    "category": "Transporte"
  }
}
```

**Retorno:**
```
-R$34.00 Uber (Transporte)
-R$28.00 99 (Transporte)
-R$45.00 Gasolina (Transporte)

Saldo: R$-107.00 | Entradas: R$0.00 | Saídas: R$107.00
```

---

### 3. monthlySummary

**Arquivo:** `tools/TransactionTools.kt`

**Descrição:** Resumo mensal agrupado por categoria.

**Parâmetros:**
| Nome | Tipo | Obrigatório | Descrição |
|------|------|-------------|-----------|
| month | String | Não | Mês em "YYYY-MM", default: mês atual |

**Retorno:**
```
📊 Resumo 2024-01
  Alimentacao: -R$450.00
  Transporte: -R$320.00
  Mercado: -R$580.00

Total saídas: R$1350.00
Total entradas: R$5000.00
Saldo: R$+3650.00
```

---

### 4. sendText

**Arquivo:** `tools/ChatwootTools.kt`

**Descrição:** Envia mensagem de texto separada para o usuário.

**Quando usar:**
- Links que precisam ser clicáveis
- Chaves PIX
- Informações formatadas que não funcionam bem em áudio

**Parâmetros:**
| Nome | Tipo | Obrigatório | Descrição |
|------|------|-------------|-----------|
| content | String | Sim | Texto a enviar |

**Retorno:** `"Message sent."`

---

### 5. reactToMessage

**Arquivo:** `tools/ChatwootTools.kt`

**Descrição:** Envia reação emoji à última mensagem do usuário.

**Limitações:**
- Máximo 3 usos por conversa
- Não usar múltiplas vezes seguidas

**Parâmetros:**
| Nome | Tipo | Obrigatório | Descrição |
|------|------|-------------|-----------|
| emoji | String | Sim | Emoji permitido: 😀 ❤️ 👍 👀 ✅ |

**Retorno:** `"Reaction sent."`

---

### 6. setResponsePreference

**Arquivo:** `tools/ChatwootTools.kt`

**Descrição:** Salva preferência do usuário para receber respostas em áudio ou texto.

**Quando usar:** Apenas quando usuário explicitamente pede.

**Parâmetros:**
| Nome | Tipo | Obrigatório | Descrição |
|------|------|-------------|-----------|
| preference | String | Sim | "audio" ou "texto" |

**Retorno:** `"Preference set to 'texto'."`

---

### 7. sendCancellationAlert

**Arquivo:** `tools/ChatwootTools.kt`

**Descrição:** Envia alerta de cancelamento para o gestor.

**Parâmetros:**
| Nome | Tipo | Obrigatório | Descrição |
|------|------|-------------|-----------|
| message | String | Sim | Mensagem com nome, data, motivo, notas |

**Retorno:** `"Alert sent."`

---

### 8. escalateToHuman

**Arquivo:** `tools/EscalationTools.kt`

**Descrição:** Transfere conversa para atendimento humano.

**Gatilhos:**
- Usuário muito insatisfeito
- Assunto fora do escopo
- Usuário pede explicitamente
- Usuário quer parar de receber mensagens

**Parâmetros:**
| Nome | Tipo | Obrigatório | Descrição |
|------|------|-------------|-----------|
| summary | String | Sim | Resumo da conversa e motivo da escalação |
| lastMessage | String | Sim | Última mensagem do usuário |

**Ações executadas:**
1. Adiciona label "agente-off" (bot para de responder)
2. Envia alerta para conversa do gestor

**Retorno:** `"Conversation escalated. A human agent will take over shortly."`

---

## Adicionando Nova Tool

### 1. Criar Request Data Class

```kotlin
data class MinhaToolRequest(
    val param1: String,
    val param2: Int? = null
)
```

### 2. Criar Bean com @Description

```kotlin
@Bean
@Description("""
    Descrição clara do que a tool faz.
    Quando usar: explicar casos de uso.
    Parameters:
    - param1: descrição do parâmetro 1
    - param2: descrição do parâmetro 2 (opcional)
""")
fun minhaTool(): Function<MinhaToolRequest, String> = Function { request ->
    val ctx = contextHolder.require()
    
    // Implementação
    
    "Resultado da tool"
}
```

### 3. Registrar no Agent

```kotlin
val chatClient = ChatClient.builder(chatModel)
    .defaultFunctions(
        "registerTransaction",
        // ... outras tools
        "minhaTool"  // Adicionar aqui
    )
    .build()
```

### 4. Documentar

Atualizar este arquivo e o system prompt do agente.

