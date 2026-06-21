package ai.koalla.core.tools

import ai.koalla.core.domain.MovementType
import ai.koalla.core.entity.TransactionEntity
import ai.koalla.core.repository.CategoryRepository
import ai.koalla.core.repository.TransactionRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Description
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.function.Function

/**
 * Spring AI function beans for financial transaction operations.
 * All functions receive context from [ToolContextHolder] which is pinned to
 * a single Dispatchers.IO thread during agent execution.
 */
@Configuration
class TransactionTools(
    private val contextHolder: ToolContextHolder,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
) {
    data class RegisterTransactionRequest(
        val description: String = "",
        val amountCents: Long = 0,
        val movement: String = "CASH_OUT",
        val categoryName: String = "Outros",
        val occurredAt: String? = null,
    )

    @Bean("registerTransaction")
    @Description(
        """
        Register a financial transaction for the user.
        Infer all fields from the user's message before calling this tool.
        Parameters:
        - description: Short description (e.g. 'Uber', 'Almoço')
        - amountCents: Amount in cents (e.g. 4500 for R$45,00)
        - movement: CASH_IN for income, CASH_OUT for expense
        - categoryName: Mercado, Diversao, Educação, Assinatura, Transporte, Alimentacao, Moradia, Lazer, Saude, Investimento, Outros, Receita
        - occurredAt: ISO date string (YYYY-MM-DD), omit for today
    """,
    )
    fun registerTransaction(): Function<RegisterTransactionRequest, String> =
        Function { req ->
            val ctx = contextHolder.require()

            val movement =
                try {
                    MovementType.valueOf(req.movement.uppercase())
                } catch (e: IllegalArgumentException) {
                    return@Function "Invalid movement type: ${req.movement}"
                }

            val occurredAt =
                if (req.occurredAt != null) {
                    try {
                        LocalDate.parse(req.occurredAt).atStartOfDay().atOffset(ZoneOffset.UTC)
                    } catch (e: Exception) {
                        OffsetDateTime.now(ZoneOffset.UTC)
                    }
                } else {
                    OffsetDateTime.now(ZoneOffset.UTC)
                }

            val category =
                categoryRepository
                    .findByUserIdIsNull()
                    .find { it.name.equals(req.categoryName, ignoreCase = true) }

            val transaction =
                TransactionEntity(
                    userId = ctx.userId,
                    description = req.description,
                    amount = req.amountCents,
                    movement = movement,
                    categoryId = category?.id,
                    source = "whatsapp",
                    occurredAt = occurredAt,
                )
            transactionRepository.save(transaction)

            val amountBrl = req.amountCents / 100.0
            val direction = if (movement == MovementType.CASH_IN) "💰 Receita" else "💸 Despesa"
            "$direction de R$${"%.2f".format(amountBrl)} (${req.description}) registrada em ${req.categoryName}."
        }

    data class ListTransactionsRequest(
        val period: String = "month",
        val category: String? = null,
    )

    @Bean("listTransactions")
    @Description(
        """
        List the user's financial transactions for a given period.
        Parameters:
        - period: 'today', 'week', 'month', or 'YYYY-MM' for a specific month
        - category: Optional category filter
    """,
    )
    fun listTransactions(): Function<ListTransactionsRequest, String> =
        Function { req ->
            val ctx = contextHolder.require()
            val now = OffsetDateTime.now(ZoneOffset.UTC)

            val (start, end) =
                when (req.period.lowercase()) {
                    "today" -> {
                        val startOfDay = now.toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC)
                        startOfDay to now
                    }
                    "week" -> {
                        val startOfWeek =
                            now
                                .toLocalDate()
                                .minusDays(now.dayOfWeek.value.toLong() - 1)
                                .atStartOfDay()
                                .atOffset(ZoneOffset.UTC)
                        startOfWeek to now
                    }
                    "month" -> {
                        val startOfMonth =
                            now
                                .toLocalDate()
                                .withDayOfMonth(1)
                                .atStartOfDay()
                                .atOffset(ZoneOffset.UTC)
                        startOfMonth to now
                    }
                    else -> {
                        try {
                            val yearMonth = YearMonth.parse(req.period)
                            val startOfMonth = yearMonth.atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC)
                            val endOfMonth =
                                yearMonth
                                    .plusMonths(1)
                                    .atDay(1)
                                    .atStartOfDay()
                                    .atOffset(ZoneOffset.UTC)
                            startOfMonth to endOfMonth
                        } catch (e: Exception) {
                            return@Function "Invalid period format. Use 'today', 'week', 'month', or 'YYYY-MM'."
                        }
                    }
                }

            val transactions = transactionRepository.findByUserIdAndPeriod(ctx.userId, start, end)

            // Single query — reused for both filtering and display
            val allCategories = categoryRepository.findByUserIdIsNull()
            val categoryMap = allCategories.associateBy { it.id }

            val filtered =
                if (req.category != null) {
                    val categoryIds =
                        allCategories
                            .filter { it.name.equals(req.category, ignoreCase = true) }
                            .mapNotNull { it.id }
                    transactions.filter { it.categoryId in categoryIds }
                } else {
                    transactions
                }

            if (filtered.isEmpty()) {
                return@Function "Nenhuma transação encontrada nesse período."
            }

            // Totals over ALL filtered transactions, not just the displayed 20
            var totalIn = 0L
            var totalOut = 0L
            for (tx in filtered) {
                if (tx.movement == MovementType.CASH_IN) totalIn += tx.amount else totalOut += tx.amount
            }

            val lines =
                filtered.take(20).map { tx ->
                    val catName = categoryMap[tx.categoryId]?.name ?: "Sem categoria"
                    val symbol = if (tx.movement == MovementType.CASH_IN) "+" else "-"
                    val amountBrl = tx.amount / 100.0
                    "${symbol}R$${"%.2f".format(amountBrl)} ${tx.description ?: ""} ($catName)"
                }

            val balance = (totalIn - totalOut) / 100.0
            val summary = "\nSaldo: R$${"%+.2f".format(
                balance,
            )} | Entradas: R$${"%.2f".format(totalIn / 100.0)} | Saídas: R$${"%.2f".format(
                totalOut / 100.0,
            )}"
            lines.joinToString("\n") + summary
        }

    data class MonthlySummaryRequest(
        val month: String? = null,
    )

    @Bean("monthlySummary")
    @Description(
        """
        Return a spending summary grouped by category for a given month.
        Parameters:
        - month: Month in YYYY-MM format. Omit for current month.
    """,
    )
    fun monthlySummary(): Function<MonthlySummaryRequest, String> =
        Function { req ->
            val ctx = contextHolder.require()
            val now = OffsetDateTime.now(ZoneOffset.UTC)

            val yearMonth =
                if (req.month != null) {
                    try {
                        YearMonth.parse(req.month)
                    } catch (e: Exception) {
                        return@Function "Invalid month format. Use YYYY-MM."
                    }
                } else {
                    YearMonth.from(now)
                }

            val start = yearMonth.atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC)
            val end =
                yearMonth
                    .plusMonths(1)
                    .atDay(1)
                    .atStartOfDay()
                    .atOffset(ZoneOffset.UTC)

            val transactions = transactionRepository.findByUserIdAndPeriod(ctx.userId, start, end)

            if (transactions.isEmpty()) {
                return@Function "Sem transações em $yearMonth."
            }

            val categoryMap = categoryRepository.findByUserIdIsNull().associateBy { it.id }

            var totalIn = 0L
            var totalOut = 0L
            val byCategory = mutableMapOf<String, MutableMap<String, Long>>()

            for (tx in transactions) {
                val catName = categoryMap[tx.categoryId]?.name ?: "Sem categoria"
                val entry = byCategory.getOrPut(catName) { mutableMapOf("in" to 0L, "out" to 0L) }
                if (tx.movement == MovementType.CASH_IN) {
                    entry["in"] = (entry["in"] ?: 0L) + tx.amount
                    totalIn += tx.amount
                } else {
                    entry["out"] = (entry["out"] ?: 0L) + tx.amount
                    totalOut += tx.amount
                }
            }

            val lines = mutableListOf("📊 Resumo $yearMonth")
            byCategory.entries
                .filter { (it.value["out"] ?: 0L) > 0 }
                .sortedByDescending { it.value["out"] }
                .forEach { (cat, vals) ->
                    lines.add("  $cat: -R$${"%.2f".format((vals["out"] ?: 0L) / 100.0)}")
                }

            lines.add("\nTotal saídas: R$${"%.2f".format(totalOut / 100.0)}")
            lines.add("Total entradas: R$${"%.2f".format(totalIn / 100.0)}")
            lines.add("Saldo: R$${"%+.2f".format((totalIn - totalOut) / 100.0)}")

            lines.joinToString("\n")
        }
}
