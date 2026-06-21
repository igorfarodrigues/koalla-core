package ai.koalla.core.service

import ai.koalla.core.domain.MovementType
import ai.koalla.core.domain.Transaction
import ai.koalla.core.dto.TransactionCreateRequest
import ai.koalla.core.dto.TransactionSummary
import ai.koalla.core.entity.TransactionEntity
import ai.koalla.core.mapper.toDomain
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
    private val categoryRepository: CategoryRepository,
) {
    @Transactional
    fun create(request: TransactionCreateRequest): Transaction {
        val entity =
            TransactionEntity(
                userId = request.userId,
                description = request.description,
                amount = request.amount,
                movement = request.movement,
                categoryId = request.categoryId,
                entityType = request.entityType,
                source = request.source,
                occurredAt = request.occurredAt ?: OffsetDateTime.now(),
            )
        return transactionRepository.save(entity).toDomain()
    }

    fun listByUser(
        userId: UUID,
        limit: Int = 50,
    ): List<Transaction> =
        transactionRepository
            .findByUserIdOrderByOccurredAtDesc(
                userId,
                PageRequest.of(0, limit),
            ).map { it.toDomain() }

    fun findById(id: UUID): Transaction? = transactionRepository.findById(id).orElse(null)?.toDomain()

    @Transactional
    fun delete(id: UUID): Boolean {
        val entity = transactionRepository.findById(id).orElse(null) ?: return false
        transactionRepository.delete(entity)
        return true
    }

    fun listByUserAndMonth(
        userId: UUID,
        year: Int,
        month: Int,
    ): List<Transaction> {
        val yearMonth = YearMonth.of(year, month)
        val start = yearMonth.atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC)
        val end =
            yearMonth
                .plusMonths(1)
                .atDay(1)
                .atStartOfDay()
                .atOffset(ZoneOffset.UTC)
        return transactionRepository.findByUserIdAndPeriod(userId, start, end).map { it.toDomain() }
    }

    fun getMonthlySummary(
        userId: UUID,
        year: Int,
        month: Int,
    ): TransactionSummary {
        val yearMonth = YearMonth.of(year, month)
        val start = yearMonth.atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC)
        val end =
            yearMonth
                .plusMonths(1)
                .atDay(1)
                .atStartOfDay()
                .atOffset(ZoneOffset.UTC)

        val cashIn = transactionRepository.sumCashInByUserIdAndPeriod(userId, start, end)
        val cashOut = transactionRepository.sumCashOutByUserIdAndPeriod(userId, start, end)

        val transactions = transactionRepository.findByUserIdAndPeriod(userId, start, end)
        val categoryIds = transactions.mapNotNull { it.categoryId }.distinct()
        val categories =
            if (categoryIds.isNotEmpty()) {
                categoryRepository.findAllById(categoryIds).associateBy { it.id }
            } else {
                emptyMap()
            }

        val byCategory =
            transactions
                .filter { it.movement == MovementType.CASH_OUT }
                .groupBy { it.categoryId }
                .mapKeys { (categoryId, _) -> categories[categoryId]?.name ?: "Outros" }
                .mapValues { (_, txs) -> txs.sumOf { it.amount } }

        return TransactionSummary(
            totalCashIn = cashIn,
            totalCashOut = cashOut,
            balance = cashIn - cashOut,
            byCategory = byCategory,
        )
    }

    @Transactional
    fun registerTransaction(
        userId: UUID,
        description: String,
        amount: Long,
        movement: MovementType,
        categoryName: String? = null,
        occurredAt: OffsetDateTime = OffsetDateTime.now(),
    ): Transaction {
        val categoryId =
            if (categoryName != null) {
                categoryRepository
                    .findByUserIdIsNull()
                    .find { it.name.equals(categoryName, ignoreCase = true) }
                    ?.id
            } else {
                null
            }

        val entity =
            TransactionEntity(
                userId = userId,
                description = description,
                amount = amount,
                movement = movement,
                categoryId = categoryId,
                occurredAt = occurredAt,
            )
        return transactionRepository.save(entity).toDomain()
    }
}
