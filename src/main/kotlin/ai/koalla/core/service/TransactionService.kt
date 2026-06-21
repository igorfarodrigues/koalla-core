package ai.koalla.core.service

import ai.koalla.core.dto.TransactionCreateRequest
import ai.koalla.core.dto.TransactionResponse
import ai.koalla.core.dto.TransactionSummary
import ai.koalla.core.entity.Category
import ai.koalla.core.entity.MovementType
import ai.koalla.core.entity.Transaction
import ai.koalla.core.repository.CategoryRepository
import ai.koalla.core.repository.TransactionRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.UUID

@Service
class TransactionService(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository
) {

    @Transactional
    fun create(request: TransactionCreateRequest): Transaction {
        val transaction = Transaction(
            userId = request.userId,
            description = request.description,
            amount = request.amount,
            movement = request.movement,
            categoryId = request.categoryId,
            entityType = request.entityType,
            source = request.source,
            occurredAt = request.occurredAt ?: OffsetDateTime.now()
        )
        return transactionRepository.save(transaction)
    }

    fun listByUser(userId: UUID, limit: Int = 50): List<Transaction> {
        return transactionRepository.findByUserIdOrderByOccurredAtDesc(
            userId,
            PageRequest.of(0, limit)
        )
    }

    fun findById(id: UUID): Transaction? {
        return transactionRepository.findById(id).orElse(null)
    }

    @Transactional
    fun delete(id: UUID): Boolean {
        val transaction = transactionRepository.findById(id).orElse(null) ?: return false
        transactionRepository.delete(transaction)
        return true
    }

    /**
     * Get transactions for a specific month.
     */
    fun listByUserAndMonth(userId: UUID, year: Int, month: Int): List<Transaction> {
        val yearMonth = YearMonth.of(year, month)
        val startDate = yearMonth.atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC)
        val endDate = yearMonth.plusMonths(1).atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC)

        return transactionRepository.findByUserIdAndPeriod(userId, startDate, endDate)
    }

    /**
     * Get monthly summary with totals by category.
     */
    fun getMonthlySummary(userId: UUID, year: Int, month: Int): TransactionSummary {
        val yearMonth = YearMonth.of(year, month)
        val startDate = yearMonth.atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC)
        val endDate = yearMonth.plusMonths(1).atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC)

        val cashIn = transactionRepository.sumCashInByUserIdAndPeriod(userId, startDate, endDate)
        val cashOut = transactionRepository.sumCashOutByUserIdAndPeriod(userId, startDate, endDate)

        // Get transactions for category breakdown
        val transactions = transactionRepository.findByUserIdAndPeriod(userId, startDate, endDate)

        // Get categories for names
        val categoryIds = transactions.mapNotNull { it.categoryId }.distinct()
        val categories = if (categoryIds.isNotEmpty()) {
            categoryRepository.findAllById(categoryIds).associateBy { it.id }
        } else {
            emptyMap()
        }

        val byCategory = transactions
            .filter { it.movement == MovementType.CASH_OUT }
            .groupBy { it.categoryId }
            .mapKeys { (categoryId, _) ->
                categories[categoryId]?.name ?: "Outros"
            }
            .mapValues { (_, txs) ->
                txs.sumOf { it.amount }
            }

        return TransactionSummary(
            totalCashIn = cashIn,
            totalCashOut = cashOut,
            balance = cashIn - cashOut,
            byCategory = byCategory
        )
    }

    /**
     * Register a transaction (used by the AI agent).
     */
    @Transactional
    fun registerTransaction(
        userId: UUID,
        description: String,
        amount: Long,
        movement: MovementType,
        categoryName: String? = null,
        occurredAt: OffsetDateTime = OffsetDateTime.now()
    ): Transaction {
        // Find or create category
        val categoryId = if (categoryName != null) {
            val existingCategory = categoryRepository.findByUserIdIsNull()
                .find { it.name.equals(categoryName, ignoreCase = true) }
            existingCategory?.id
        } else null

        val transaction = Transaction(
            userId = userId,
            description = description,
            amount = amount,
            movement = movement,
            categoryId = categoryId,
            occurredAt = occurredAt
        )
        return transactionRepository.save(transaction)
    }

    fun toResponse(transaction: Transaction): TransactionResponse {
        return TransactionResponse(
            id = transaction.id!!,
            userId = transaction.userId,
            description = transaction.description,
            amount = transaction.amount,
            movement = transaction.movement,
            categoryId = transaction.categoryId,
            entityType = transaction.entityType,
            source = transaction.source,
            occurredAt = transaction.occurredAt,
            createdAt = transaction.createdAt,
            updatedAt = transaction.updatedAt
        )
    }
}

