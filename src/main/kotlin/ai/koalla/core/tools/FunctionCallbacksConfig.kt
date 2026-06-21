package ai.koalla.core.tools

import ai.koalla.core.client.ChatwootClient
import ai.koalla.core.entity.MovementType
import ai.koalla.core.entity.Transaction
import ai.koalla.core.repository.CategoryRepository
import ai.koalla.core.repository.TransactionRepository
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.runBlocking
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Description
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.function.Function

/**
 * Spring AI Function configuration.
 * Registers all tools as Function beans with @Description for the agent.
 * Spring AI auto-discovers @Bean methods returning Function with @Description.
 */
@Configuration
class FunctionCallbacksConfig(
    private val contextHolder: ToolContextHolder,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val chatwootClient: ChatwootClient,
    private val objectMapper: ObjectMapper
) {

    // ═══════════════════════════════════════════════════════════════════════════
    // TRANSACTION TOOLS
    // ═══════════════════════════════════════════════════════════════════════════

    data class RegisterTransactionRequest(
        val description: String = "",
        val amountCents: Long = 0,
        val movement: String = "CASH_OUT",
        val categoryName: String = "Outros",
        val occurredAt: String? = null
    )

    @Bean("registerTransaction")
    @Description("""
        Register a financial transaction for the user.
        Infer all fields from the user's message before calling this tool.
        Parameters:
        - description: Short description (e.g. 'Uber', 'Almoço')
        - amountCents: Amount in cents (e.g. 4500 for R$45,00)
        - movement: CASH_IN for income, CASH_OUT for expense
        - categoryName: Mercado, Diversao, Educação, Assinatura, Transporte, Alimentacao, Moradia, Lazer, Saude, Investimento, Outros, Receita
        - occurredAt: ISO date string (YYYY-MM-DD), omit for today
    """)
    fun registerTransaction(): Function<RegisterTransactionRequest, String> = Function { req ->
        val ctx = contextHolder.require()

        val movement = try {
            MovementType.valueOf(req.movement.uppercase())
        } catch (e: IllegalArgumentException) {
            return@Function "Invalid movement type: ${req.movement}"
        }

        val occurredAt = if (req.occurredAt != null) {
            try {
                LocalDate.parse(req.occurredAt).atStartOfDay().atOffset(ZoneOffset.UTC)
            } catch (e: Exception) {
                OffsetDateTime.now(ZoneOffset.UTC)
            }
        } else {
            OffsetDateTime.now(ZoneOffset.UTC)
        }

        val category = categoryRepository.findByUserIdIsNull()
            .find { it.name.equals(req.categoryName, ignoreCase = true) }

        val transaction = Transaction(
            userId = ctx.userId,
            description = req.description,
            amount = req.amountCents,
            movement = movement,
            categoryId = category?.id,
            source = "whatsapp",
            occurredAt = occurredAt
        )
        transactionRepository.save(transaction)

        val amountBrl = req.amountCents / 100.0
        val direction = if (movement == MovementType.CASH_IN) "💰 Receita" else "💸 Despesa"
        "$direction de R$${"%.2f".format(amountBrl)} (${req.description}) registrada em ${req.categoryName}."
    }

    data class ListTransactionsRequest(
        val period: String = "month",
        val category: String? = null
    )

    @Bean("listTransactions")
    @Description("""
        List the user's financial transactions for a given period.
        Parameters:
        - period: 'today', 'week', 'month', or 'YYYY-MM' for a specific month
        - category: Optional category filter
    """)
    fun listTransactions(): Function<ListTransactionsRequest, String> = Function { req ->
        val ctx = contextHolder.require()
        val now = OffsetDateTime.now(ZoneOffset.UTC)

        val (start, end) = when (req.period.lowercase()) {
            "today" -> {
                val startOfDay = now.toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC)
                startOfDay to now
            }
            "week" -> {
                val startOfWeek = now.toLocalDate().minusDays(now.dayOfWeek.value.toLong() - 1)
                    .atStartOfDay().atOffset(ZoneOffset.UTC)
                startOfWeek to now
            }
            "month" -> {
                val startOfMonth = now.toLocalDate().withDayOfMonth(1).atStartOfDay().atOffset(ZoneOffset.UTC)
                startOfMonth to now
            }
            else -> {
                try {
                    val yearMonth = YearMonth.parse(req.period)
                    val startOfMonth = yearMonth.atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC)
                    val endOfMonth = yearMonth.plusMonths(1).atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC)
                    startOfMonth to endOfMonth
                } catch (e: Exception) {
                    return@Function "Invalid period format. Use 'today', 'week', 'month', or 'YYYY-MM'."
                }
            }
        }

        val transactions = transactionRepository.findByUserIdAndPeriod(ctx.userId, start, end)

        // Single query for all categories, reused for both filtering and display
        val allCategories = categoryRepository.findByUserIdIsNull()
        val categoryMap = allCategories.associateBy { it.id }

        val filtered = if (req.category != null) {
            val categoryIds = allCategories
                .filter { it.name.equals(req.category, ignoreCase = true) }
                .mapNotNull { it.id }
            transactions.filter { it.categoryId in categoryIds }
        } else {
            transactions
        }

        if (filtered.isEmpty()) {
            return@Function "Nenhuma transação encontrada nesse período."
        }

        // Calculate totals over ALL filtered transactions, not just the displayed 20
        var totalIn = 0L
        var totalOut = 0L
        for (tx in filtered) {
            if (tx.movement == MovementType.CASH_IN) totalIn += tx.amount else totalOut += tx.amount
        }

        val lines = filtered.take(20).map { tx ->
            val catName = categoryMap[tx.categoryId]?.name ?: "Sem categoria"
            val symbol = if (tx.movement == MovementType.CASH_IN) "+" else "-"
            val amountBrl = tx.amount / 100.0
            "${symbol}R$${"%.2f".format(amountBrl)} ${tx.description ?: ""} ($catName)"
        }

        val balance = (totalIn - totalOut) / 100.0
        val summary = "\nSaldo: R$${"%+.2f".format(balance)} | Entradas: R$${"%.2f".format(totalIn/100.0)} | Saídas: R$${"%.2f".format(totalOut/100.0)}"
        lines.joinToString("\n") + summary
    }

    data class MonthlySummaryRequest(
        val month: String? = null
    )

    @Bean("monthlySummary")
    @Description("""
        Return a spending summary grouped by category for a given month.
        Parameters:
        - month: Month in YYYY-MM format. Omit for current month.
    """)
    fun monthlySummary(): Function<MonthlySummaryRequest, String> = Function { req ->
        val ctx = contextHolder.require()
        val now = OffsetDateTime.now(ZoneOffset.UTC)

        val yearMonth = if (req.month != null) {
            try {
                YearMonth.parse(req.month)
            } catch (e: Exception) {
                return@Function "Invalid month format. Use YYYY-MM."
            }
        } else {
            YearMonth.from(now)
        }

        val start = yearMonth.atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC)
        val end = yearMonth.plusMonths(1).atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC)

        val transactions = transactionRepository.findByUserIdAndPeriod(ctx.userId, start, end)

        if (transactions.isEmpty()) {
            return@Function "Sem transações em ${yearMonth}."
        }

        val categoryMap = categoryRepository.findByUserIdIsNull().associateBy { it.id }

        var totalIn = 0L
        var totalOut = 0L
        val byCategory = mutableMapOf<String, MutableMap<String, Long>>()

        for (tx in transactions) {
            val catName = categoryMap[tx.categoryId]?.name ?: "Sem categoria"
            val entry = byCategory.getOrPut(catName) { mutableMapOf("in" to 0L, "out" to 0L) }
            if (tx.movement == MovementType.CASH_IN) {
                entry["in"] = (entry["in"] ?: 0) + tx.amount
                totalIn += tx.amount
            } else {
                entry["out"] = (entry["out"] ?: 0) + tx.amount
                totalOut += tx.amount
            }
        }

        val lines = mutableListOf("📊 Resumo $yearMonth")
        byCategory.entries
            .filter { it.value["out"]!! > 0 }
            .sortedByDescending { it.value["out"] }
            .forEach { (cat, vals) ->
                lines.add("  $cat: -R$${"%.2f".format(vals["out"]!!/100.0)}")
            }

        lines.add("\nTotal saídas: R$${"%.2f".format(totalOut/100.0)}")
        lines.add("Total entradas: R$${"%.2f".format(totalIn/100.0)}")
        lines.add("Saldo: R$${"%+.2f".format((totalIn-totalOut)/100.0)}")

        lines.joinToString("\n")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CHATWOOT TOOLS
    // ═══════════════════════════════════════════════════════════════════════════

    data class SendTextRequest(val content: String = "")

    @Bean("sendText")
    @Description("""
        Send a text message to the user via Chatwoot.
        Use for links, phone numbers, PIX keys, or formatted info.
        NEVER include the sent data again in your output after calling this tool.
        Parameters:
        - content: The text message to send
    """)
    fun sendText(): Function<SendTextRequest, String> = Function { req ->
        val ctx = contextHolder.require()
        runBlocking {
            chatwootClient.sendMessage(ctx.accountId, ctx.conversationId, req.content)
        }
        "Message sent."
    }

    data class ReactToMessageRequest(val emoji: String = "👍")

    @Bean("reactToMessage")
    @Description("""
        Send an emoji reaction to the user's last message.
        Use max 3 times per conversation. NEVER use multiple times in a row.
        Parameters:
        - emoji: Allowed: 😀 ❤️ 👍 👀 ✅
    """)
    fun reactToMessage(): Function<ReactToMessageRequest, String> = Function { req ->
        val ctx = contextHolder.require()
        val allowedEmojis = setOf("😀", "❤️", "👍", "👀", "✅")
        if (req.emoji !in allowedEmojis) {
            return@Function "Invalid emoji. Allowed: ${allowedEmojis.joinToString(" ")}"
        }
        runBlocking {
            chatwootClient.sendReaction(ctx.accountId, ctx.conversationId, ctx.messageId, req.emoji)
        }
        "Reaction sent."
    }

    data class SetResponsePreferenceRequest(val preference: String = "texto")

    @Bean("setResponsePreference")
    @Description("""
        Save user's preference for audio or text responses.
        Only use when user explicitly requests.
        Parameters:
        - preference: 'audio' or 'texto'
    """)
    fun setResponsePreference(): Function<SetResponsePreferenceRequest, String> = Function { req ->
        val ctx = contextHolder.require()
        val validPrefs = setOf("audio", "texto")
        if (req.preference.lowercase() !in validPrefs) {
            return@Function "Invalid preference. Use 'audio' or 'texto'."
        }
        val currentAttrs = ctx.contactCustomAttributes.toMutableMap()
        currentAttrs["preferencia_audio_texto"] = req.preference.lowercase()
        runBlocking {
            chatwootClient.updateContactAttributes(ctx.accountId, ctx.contactId, currentAttrs)
        }
        "Preference set to '${req.preference}'."
    }

    data class SendCancellationAlertRequest(val message: String = "")

    @Bean("sendCancellationAlert")
    @Description("""
        Send a cancellation alert to the manager's conversation.
        Parameters:
        - message: Alert with name, date/time, reason and notes
    """)
    fun sendCancellationAlert(): Function<SendCancellationAlertRequest, String> = Function { req ->
        val ctx = contextHolder.require()
        val alertConvId = ctx.alertConversationId
        if (alertConvId.isBlank()) {
            return@Function "Alert conversation not configured."
        }
        val alertConvIdInt = alertConvId.toIntOrNull()
            ?: return@Function "Invalid alert conversation ID."
        runBlocking {
            chatwootClient.sendMessage(ctx.accountId, alertConvIdInt, req.message)
        }
        "Alert sent."
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ESCALATION TOOLS
    // ═══════════════════════════════════════════════════════════════════════════

    data class EscalateToHumanRequest(
        val summary: String = "",
        val lastMessage: String = ""
    )

    @Bean("escalateToHuman")
    @Description("""
        Escalate conversation to human agent immediately.
        Use when: user is dissatisfied, topic out of scope, user asks for human, or wants to stop messages.
        Parameters:
        - summary: Brief summary of conversation and why it needs human attention
        - lastMessage: User's last message
    """)
    fun escalateToHuman(): Function<EscalateToHumanRequest, String> = Function { req ->
        val ctx = contextHolder.require()
        runBlocking {
            val currentLabels = ctx.labels.toMutableSet()
            currentLabels.add("agente-off")
            chatwootClient.updateLabels(ctx.accountId, ctx.conversationId, currentLabels.toList())

            val alertConvId = ctx.alertConversationId
            if (alertConvId.isNotBlank()) {
                val alertConvIdInt = alertConvId.toIntOrNull()
                if (alertConvIdInt != null) {
                    val alertMsg = """
                        |🚨 *Escalação humana*
                        |Nome: ${ctx.contactName}
                        |Resumo: ${req.summary}
                        |Última mensagem: ${req.lastMessage}
                    """.trimMargin()
                    chatwootClient.sendMessage(ctx.accountId, alertConvIdInt, alertMsg)
                }
            }
        }
        "Conversation escalated. A human agent will take over shortly."
    }
}

