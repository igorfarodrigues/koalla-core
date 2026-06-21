package ai.koalla.core.agent

import ai.koalla.core.client.ChatwootClient
import ai.koalla.core.config.KoallaProperties
import ai.koalla.core.entity.MovementType
import ai.koalla.core.repository.ChatHistoryRepository
import ai.koalla.core.service.AgentContext
import ai.koalla.core.service.TransactionService
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Koalla — the intelligent financial assistant via WhatsApp.
 * Implemented with Spring AI and OpenAI.
 */
@Component
class KoallaAgent(
    private val chatModel: OpenAiChatModel,
    private val chatHistoryRepository: ChatHistoryRepository,
    private val transactionService: TransactionService,
    private val chatwootClient: ChatwootClient,
    private val props: KoallaProperties,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private val SYSTEM_PROMPT = """
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
Mercado, Diversao, Educação, Assinatura, Transporte, Alimentacao, Moradia, Lazer, 
Saude, Investimento, Outros, Receita

### Inferência de tipo:
* Padrão: CASH_OUT (despesa)
* Só usar CASH_IN se houver: recebi, salário, pix recebido, entrou, freela

# COMPORTAMENTO PADRÃO (EXEMPLOS)
* "Almoço 45" → registrar: Almoço, R${'$'}45, Alimentacao, hoje, CASH_OUT → "✅ Almoço R${'$'}45 em Alimentação."
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
- register_transaction: registra receita ou despesa
- list_transactions: lista transações por período
- monthly_summary: resumo mensal por categoria

# REGRA DE OURO
Se deu para inferir, EXECUTE. Se não deu, pergunte UMA vez.
        """.trimIndent()
    }

    // Thread-local context for tools
    private val contextHolder = ThreadLocal<AgentContext>()

    /**
     * Run the Koalla agent for a given message and session.
     * Returns the agent's text output.
     */
    suspend fun runAgent(message: String, sessionId: String, context: AgentContext): String? {
        contextHolder.set(context)
        try {
            // Load conversation history from database
            val historyRecords = chatHistoryRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)
                .takeLast(props.memoryWindowLength)

            val chatHistory = historyRecords.mapNotNull { record ->
                try {
                    val msgData = objectMapper.readValue(record.message, Map::class.java)
                    when (msgData["role"]) {
                        "user" -> UserMessage(msgData["content"] as? String ?: "")
                        "assistant" -> AssistantMessage(msgData["content"] as? String ?: "")
                        else -> null
                    }
                } catch (e: Exception) {
                    null
                }
            }

            // Build messages
            val systemPrompt = SYSTEM_PROMPT.replace(
                "{current_date}",
                OffsetDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            )

            val messages = mutableListOf<Message>()
            messages.add(SystemMessage(systemPrompt))
            messages.addAll(chatHistory)
            messages.add(UserMessage(message))

            // Call the model
            val chatClient = ChatClient.create(chatModel)

            // For now, we'll use a simpler approach without function calling
            // and handle tool logic in the response parsing
            val response = chatClient.prompt()
                .messages(messages)
                .call()
                .content()

            // Save to history
            saveToHistory(sessionId, "user", message)
            if (response != null) {
                saveToHistory(sessionId, "assistant", response)
            }

            // Check if response indicates a transaction registration
            val processedResponse = processAgentResponse(response ?: "", message, context)

            return processedResponse
        } finally {
            contextHolder.remove()
        }
    }

    /**
     * Process agent response and handle implicit tool calls.
     * This is a simplified version - in production, you'd use proper function calling.
     */
    private fun processAgentResponse(response: String, originalMessage: String, context: AgentContext): String {
        // Simple heuristic: if the message contains a number and looks like a transaction
        val amountRegex = Regex("""(\d+[.,]?\d*)\s*(reais|R\$)?""", RegexOption.IGNORE_CASE)
        val match = amountRegex.find(originalMessage)

        if (match != null && (response.contains("✅") || response.contains("💰") || response.contains("🚗"))) {
            // Transaction was likely registered by the model's response
            // In a full implementation, we'd parse structured output and call the service
            try {
                val amountStr = match.groupValues[1].replace(",", ".")
                val amount = (amountStr.toDouble() * 100).toLong()

                val movement = if (originalMessage.lowercase().contains(Regex("recebi|salário|pix recebido|entrou|freela"))) {
                    MovementType.CASH_IN
                } else {
                    MovementType.CASH_OUT
                }

                val category = inferCategory(originalMessage)
                val description = originalMessage.replace(Regex("""\d+[.,]?\d*"""), "").trim()
                    .take(100)
                    .ifEmpty { category }

                transactionService.registerTransaction(
                    userId = context.userId,
                    description = description,
                    amount = amount,
                    movement = movement,
                    categoryName = category,
                    occurredAt = inferDate(originalMessage)
                )
            } catch (e: Exception) {
                logger.debug("Could not auto-register transaction: ${e.message}")
            }
        }

        return response
    }

    private fun inferCategory(message: String): String {
        val lower = message.lowercase()
        return when {
            lower.contains("uber") || lower.contains("taxi") || lower.contains("99") || lower.contains("transporte") -> "Transporte"
            lower.contains("almoço") || lower.contains("janta") || lower.contains("lanche") || lower.contains("comida") -> "Alimentacao"
            lower.contains("mercado") || lower.contains("supermercado") || lower.contains("feira") -> "Mercado"
            lower.contains("netflix") || lower.contains("spotify") || lower.contains("assinatura") -> "Assinatura"
            lower.contains("cinema") || lower.contains("show") || lower.contains("diversão") || lower.contains("bar") -> "Diversao"
            lower.contains("escola") || lower.contains("curso") || lower.contains("livro") -> "Educação"
            lower.contains("aluguel") || lower.contains("condomínio") || lower.contains("luz") || lower.contains("água") -> "Moradia"
            lower.contains("médico") || lower.contains("farmácia") || lower.contains("remédio") || lower.contains("saúde") -> "Saude"
            lower.contains("investimento") || lower.contains("ação") || lower.contains("fundo") -> "Investimento"
            lower.contains("recebi") || lower.contains("salário") || lower.contains("freela") || lower.contains("pix recebido") -> "Receita"
            else -> "Outros"
        }
    }

    private fun inferDate(message: String): OffsetDateTime {
        val lower = message.lowercase()
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        return when {
            lower.contains("ontem") -> now.minusDays(1)
            lower.contains("anteontem") -> now.minusDays(2)
            else -> now
        }
    }

    private fun saveToHistory(sessionId: String, role: String, content: String) {
        try {
            val messageJson = objectMapper.writeValueAsString(mapOf(
                "role" to role,
                "content" to content
            ))
            chatHistoryRepository.save(
                ai.koalla.core.entity.ChatHistory(
                    sessionId = sessionId,
                    message = messageJson
                )
            )
        } catch (e: Exception) {
            logger.warn("Failed to save chat history: ${e.message}")
        }
    }

    /**
     * Transcribe audio using OpenAI Whisper API.
     */
    suspend fun transcribeAudio(audioBytes: ByteArray): String {
        // For now, return a placeholder - full implementation would use OpenAI Whisper API
        logger.info("Audio transcription requested (${audioBytes.size} bytes)")
        return "[Áudio recebido - transcrição não implementada]"
    }

    /**
     * Format agent output for WhatsApp:
     * - Replace ** with *
     * - Remove #
     * - Clean up formatting
     */
    suspend fun formatForWhatsApp(text: String): String {
        return text
            .replace("**", "*")
            .replace(Regex("#+\\s*"), "")
            .trim()
    }
}

