package ai.koalla.core.agent

import ai.koalla.core.config.KoallaProperties
import ai.koalla.core.domain.AgentContext
import ai.koalla.core.repository.ChatHistoryRepository
import ai.koalla.core.tools.ToolContextHolder
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Koalla — the intelligent financial assistant via WhatsApp.
 * Implemented with Spring AI and OpenAI function calling.
 *
 * Function calling is configured via @Bean functions with @Description in dedicated tool classes:
 *   - TransactionTools: register, list, summary
 *   - ChatwootTools: sendText, react, preferences, alerts
 *   - EscalationTools: escalateToHuman
 *
 * Spring AI auto-discovers these functions and makes them available to the ChatClient.
 */
@Component
class KoallaAgent(
    private val chatModel: ChatModel,
    private val chatHistoryRepository: ChatHistoryRepository,
    private val toolContextHolder: ToolContextHolder,
    private val props: KoallaProperties,
    private val objectMapper: ObjectMapper,
    private val chatClientBuilder: ChatClient.Builder,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private val SYSTEM_PROMPT =
            """
# PAPEL
Você é o Koalla.ai, assistente financeiro inteligente que ajuda o usuário a registrar,
organizar e entender sua vida financeira através do WhatsApp. Seu papel é fazer a gestão
financeira ativa e automática: registrar gastos e receitas, categorizar transações,
acompanhar histórico financeiro e fornecer clareza sobre para onde o dinheiro está indo
— com o mínimo de fricção possível.

# PRINCÍPIO FUNDAMENTAL
Koalla NÃO é uma atendente passiva. Koalla é um motor financeiro inteligente.
Seu objetivo é:
* Registrar transações automaticamente
* Inferir dados sem perguntar quando possível
* Reduzir esforço cognitivo do usuário
* Evitar perguntas desnecessárias

# CONTEXTO DINÂMICO
* Data atual: {current_date}
* Moeda padrão: BRL (R$)

# REGRAS DE INFERÊNCIA (AUTOMAÇÃO OBRIGATÓRIA)
Ao receber qualquer mensagem curta contendo valor monetário, Koalla DEVE assumir que se
trata de um registro financeiro.

### Categorias disponíveis:
Mercado, Diversão, Educação, Assinatura, Transporte, Alimentação, Moradia, Lazer,
Saúde, Investimento, Outros, Receita

### Inferência de tipo:
* Padrão: CASH_OUT (despesa)
* Só usar CASH_IN se houver: recebi, salário, pix recebido, entrou, freela

# COMPORTAMENTO PADRÃO (EXEMPLOS)
* "Almoço 45" → registrar: Almoço, R${'$'}45, Alimentação, hoje, CASH_OUT → "✅ Almoço R${'$'}45 em Alimentação."
* "Recebi 500 do freela ontem" → registrar: Freela, R${'$'}500, Receita, ontem, CASH_IN → "💰 Receita R${'$'}500 registrada para ontem."
* "Uber 34" → registrar → "🚗 Uber R${'$'}34 em Transporte."

# REGRAS DE COMUNICAÇÃO
* Respostas curtas e confirmatórias
* Emojis apenas para confirmação visual
* Nunca listar regras internas ao usuário
* Nunca pedir confirmação se a inferência for clara

# PERGUNTAS PERMITIDAS (SÓ QUANDO NECESSÁRIO)
Só perguntar se faltar informação CRÍTICA (valor não identificado ou mensagem ambígua).

# FERRAMENTAS DISPONÍVEIS
- registerTransaction: registra receita ou despesa
- listTransactions: lista transações por período
- monthlySummary: resumo mensal por categoria
- sendText: envia texto separado (use para links, PIX, etc.)
- reactToMessage: reação emoji (máx 3/conversa)
- setResponsePreference: salva preferência audio/texto
- sendCancellationAlert: alerta de cancelamento ao gestor
- escalateToHuman: escalar para atendimento humano

# REGRA DE OURO
Se deu para inferir, EXECUTE. Se não deu, pergunte UMA vez.
            """.trimIndent()

        private val FORMATTING_PROMPT =
            """
Você é especialista em formatação de mensagem para WhatsApp, trabalhando somente na formatação e não alterando o conteúdo da mensagem.
- Substitua ** por *
- Remova #
- Remova emojis duplicados ou excessivos

SUA SAÍDA DEVE SER SOMENTE A MENSAGEM FORMATADA.
            """.trimIndent()
    }

    // ChatClient with functions auto-configured via Spring AI
    private val chatClient: ChatClient by lazy {
        chatClientBuilder.build()
    }

    /**
     * Run the Koalla agent for a given message and session.
     * Uses Spring AI function calling for tool execution.
     *
     * The entire LLM call runs inside withContext(Dispatchers.IO) to ensure
     * thread affinity: Spring AI's Function callbacks (tools) are synchronous
     * and rely on ThreadLocal context. Pinning to an IO thread guarantees the
     * ThreadLocal set before the call is visible inside every tool invocation.
     */
    suspend fun runAgent(
        message: String,
        sessionId: String,
        context: AgentContext,
    ): String? {
        // Load history before entering IO context (avoids nested dispatchers)
        val historyRecords =
            chatHistoryRepository
                .findBySessionIdOrderByCreatedAtAsc(sessionId)
                .takeLast(props.memoryWindowLength)

        val chatHistory =
            historyRecords.mapNotNull { record ->
                try {
                    @Suppress("UNCHECKED_CAST")
                    val msgData = objectMapper.readValue(record.message, Map::class.java) as Map<String, Any>
                    val role = msgData["role"] as? String
                    val content = msgData["content"] as? String ?: ""
                    when (role) {
                        "user" -> UserMessage(content)
                        "assistant" -> AssistantMessage(content)
                        else -> null
                    }
                } catch (e: Exception) {
                    null
                }
            }

        val systemPrompt =
            SYSTEM_PROMPT.replace(
                "{current_date}",
                OffsetDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
            )

        val messages = mutableListOf<Message>()
        messages.add(SystemMessage(systemPrompt))
        messages.addAll(chatHistory)
        messages.add(UserMessage(message))

        // Pin to IO thread: toolContextHolder (ThreadLocal) and chatClient.call()
        // (blocking) both require stable thread affinity for correct behavior.
        return withContext(Dispatchers.IO) {
            toolContextHolder.set(context)
            try {
                val response =
                    chatClient
                        .prompt()
                        .messages(messages)
                        .call()
                        .content()

                saveToHistory(sessionId, "user", message)
                if (response != null) {
                    saveToHistory(sessionId, "assistant", response)
                }

                response
            } catch (e: Exception) {
                logger.error("Agent execution failed: ${e.message}", e)
                throw e
            } finally {
                toolContextHolder.clear()
            }
        }
    }

    private fun saveToHistory(
        sessionId: String,
        role: String,
        content: String,
    ) {
        try {
            val messageJson =
                objectMapper.writeValueAsString(
                    mapOf(
                        "role" to role,
                        "content" to content,
                    ),
                )
            chatHistoryRepository.save(
                ai.koalla.core.entity.ChatHistory(
                    sessionId = sessionId,
                    message = messageJson,
                ),
            )
        } catch (e: Exception) {
            logger.warn("Failed to save chat history: ${e.message}")
        }
    }

    /**
     * Format agent output for WhatsApp using LLM.
     * Reuses the shared chatClient (lazy) instead of creating a new instance per call.
     * Falls back to regex if the LLM call fails.
     */
    suspend fun formatForWhatsApp(text: String): String {
        // Quick check — if already well-formatted, skip LLM call
        if (!text.contains("**") && !text.contains("#")) {
            return text.trim()
        }

        return withContext(Dispatchers.IO) {
            try {
                val response =
                    chatClient
                        .prompt()
                        .system(FORMATTING_PROMPT)
                        .user(text)
                        .call()
                        .content()

                response?.trim() ?: text.trim()
            } catch (e: Exception) {
                logger.warn("Formatting failed, using fallback: ${e.message}")
                text
                    .replace("**", "*")
                    .replace(Regex("#+\\s*"), "")
                    .trim()
            }
        }
    }
}
