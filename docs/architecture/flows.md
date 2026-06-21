# Fluxos do Sistema

## 1. Fluxo Principal: Webhook Chatwoot

Pipeline completo de processamento de mensagens WhatsApp.

```
┌─────────────────────────────────────────────────────────────────┐
│                    WEBHOOK CHATWOOT                              │
│                 POST /webhook/chatwoot                          │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ 1. EXTRACT INFO                                                  │
│    • message_type, labels, wa_id, content                       │
│    • account_id, conversation_id, contact_id                    │
│    • is_audio (from attachments)                                │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ 2. ROUTE: /reset                                                 │
│    Se content == "/reset":                                       │
│    • Limpar chat_history                                        │
│    • Limpar message_queue                                       │
│    • Resetar conversation_status                                │
│    • Remover custom_attributes do contato                       │
│    • Enviar "Memória resetada."                                 │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ 3. ROUTE: /teste                                                 │
│    Se content == "/teste":                                       │
│    • Adicionar label "testando-agente"                          │
│    • Enviar "Modo de teste habilitado."                         │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ 4. FILTER                                                        │
│    • message_type != "incoming" → SKIP                          │
│    • labels ∩ {agente-off, gestor, testando-agente} → SKIP      │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ 5. GATE: USER VALIDATION                                         │
│    • User not found → "Cadastre-se em koalla.ai"                │
│    • User inactive → "Assinatura inativa"                       │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ 6. RESOLVE MESSAGE                                               │
│    • is_audio → AudioService.transcribe()                       │
│    • has_file → append file_info                                │
│    • text → use as-is                                           │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ 7. ENQUEUE MESSAGE                                               │
│    • Save to message_queue(wa_id, message_id, content)          │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ 8. DEBOUNCE                                                      │
│    • Wait MESSAGE_QUEUE_WAIT_SECONDS (default: 2s)              │
│    • Check if this is still the latest message                  │
│    • If newer message arrived → SKIP (let newer handle)         │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ 9. LOCK CHECK                                                    │
│    • If conversation locked, wait up to 5 cycles                │
│    • If still locked → SKIP                                     │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ 10. LOCK & CLEAR                                                 │
│     • Lock conversation                                         │
│     • Clear message_queue                                       │
│     • Combine all queued messages                               │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ 11. MARK AS READ                                                 │
│     • Chatwoot: update_last_seen                                │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ 12. BUILD CONTEXT                                                │
│     • Get fresh contact attributes                              │
│     • Build AgentContext with all IDs                           │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ 13. RUN AGENT                                                    │
│     • KoallaAgent.runAgent(message, sessionId, context)         │
│     • Function calling with 8 tools                             │
│     • Save to chat_history                                      │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ 14. FORMAT OUTPUT                                                │
│     • formatForWhatsApp() - LLM or regex fallback               │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ 15. SEND RESPONSE                                                │
│     • Split into chunks ≤ 4096 chars                            │
│     • Chatwoot: send_message for each chunk                     │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ 16. UNLOCK                                                       │
│     • Release conversation lock                                 │
└─────────────────────────────────────────────────────────────────┘
```

## 2. Fluxo de Function Calling

Como o agente executa tools durante a conversa.

```
┌─────────────────────────────────────────────────────────────────┐
│                    KoallaAgent.runAgent()                        │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ 1. SET TOOL CONTEXT                                              │
│    • ToolContextHolder.set(AgentContext)                        │
│    • Tools can access userId, conversationId, etc.              │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ 2. LOAD HISTORY                                                  │
│    • Fetch last N messages from chat_history                    │
│    • Convert to UserMessage/AssistantMessage                    │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ 3. BUILD PROMPT                                                  │
│    • System prompt with current date                            │
│    • Chat history                                               │
│    • Current user message                                       │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ 4. CALL OPENAI                                                   │
│    • ChatClient with 8 functions registered                     │
│    • Model decides to call function or respond                  │
└─────────────────────────────────────────────────────────────────┘
                              │
              ┌───────────────┴───────────────┐
              │                               │
              ▼                               ▼
┌─────────────────────┐         ┌─────────────────────┐
│ FUNCTION CALL       │         │ DIRECT RESPONSE     │
│ • Spring AI invokes │         │ • Return content    │
│   the @Bean function│         │                     │
│ • Tool uses context │         │                     │
│ • Returns result    │         │                     │
└─────────────────────┘         └─────────────────────┘
              │                               │
              └───────────────┬───────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ 5. SAVE HISTORY                                                  │
│    • Save user message                                          │
│    • Save assistant response                                    │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ 6. CLEAR CONTEXT                                                 │
│    • ToolContextHolder.clear()                                  │
└─────────────────────────────────────────────────────────────────┘
```

## 3. Fluxo de Escalação

Quando o agente transfere para atendimento humano.

```
User: "Quero falar com um humano"
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ Agent detects escalation intent                                  │
│ Calls: escalateToHuman(summary, lastMessage)                    │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ 1. ADD LABEL                                                     │
│    • Add "agente-off" to conversation labels                    │
│    • Bot will stop responding to this conversation              │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ 2. NOTIFY MANAGER                                                │
│    • Send alert to ALERT_CONVERSATION_ID                        │
│    • Include: name, summary, last message                       │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ 3. RETURN CONFIRMATION                                           │
│    • "Conversation escalated. Human will take over."            │
└─────────────────────────────────────────────────────────────────┘
```

## 4. Fluxo de Áudio

Processamento de mensagens de voz do WhatsApp.

```
┌─────────────────────────────────────────────────────────────────┐
│ WhatsApp Voice Message (ogg/opus)                                │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ 1. DETECT AUDIO                                                  │
│    • attachment.is_recorded_audio == true                       │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ 2. DOWNLOAD                                                      │
│    • ChatwootClient.downloadAttachment(dataUrl)                 │
│    • Returns byte array                                         │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ 3. TRANSCRIBE                                                    │
│    • AudioService.transcribe(bytes)                             │
│    • Detect format from magic bytes                             │
│    • Call OpenAI Whisper API                                    │
│    • Return transcribed text                                    │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ 4. PROCESS AS TEXT                                               │
│    • Transcribed text enters normal pipeline                    │
│    • Agent processes like any other message                     │
└─────────────────────────────────────────────────────────────────┘
```

