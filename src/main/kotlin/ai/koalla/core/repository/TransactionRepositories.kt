package ai.koalla.core.repository

import ai.koalla.core.entity.CategoryEntity
import ai.koalla.core.entity.CategoryKeywordEntity
import ai.koalla.core.entity.TransactionEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.UUID

@Repository
interface CategoryRepository : JpaRepository<CategoryEntity, Int> {
    fun findByUserId(userId: UUID): List<CategoryEntity>
    fun findByUserIdIsNull(): List<CategoryEntity>
}

@Repository
interface CategoryKeywordRepository : JpaRepository<CategoryKeywordEntity, Int> {
    fun findByCategoryId(categoryId: Int): List<CategoryKeywordEntity>
}

@Repository
interface TransactionRepository : JpaRepository<TransactionEntity, UUID> {

    fun findByUserIdOrderByOccurredAtDesc(userId: UUID, pageable: Pageable): List<TransactionEntity>

    fun findByUserIdOrderByCreatedAtDesc(userId: UUID, pageable: Pageable): List<TransactionEntity>

    @Query("""
        SELECT t FROM TransactionEntity t
        WHERE t.userId = :userId
        AND t.occurredAt >= :startDate
        AND t.occurredAt < :endDate
        ORDER BY t.occurredAt DESC
    """)
    fun findByUserIdAndPeriod(
        userId: UUID,
        startDate: OffsetDateTime,
        endDate: OffsetDateTime
    ): List<TransactionEntity>

    @Query("""
        SELECT COALESCE(SUM(t.amount), 0) FROM TransactionEntity t
        WHERE t.userId = :userId
        AND t.movement = 'CASH_IN'
        AND t.occurredAt >= :startDate
        AND t.occurredAt < :endDate
    """)
    fun sumCashInByUserIdAndPeriod(
        userId: UUID,
        startDate: OffsetDateTime,
        endDate: OffsetDateTime
    ): Long

    @Query("""
        SELECT COALESCE(SUM(t.amount), 0) FROM TransactionEntity t
        WHERE t.userId = :userId
        AND t.movement = 'CASH_OUT'
        AND t.occurredAt >= :startDate
        AND t.occurredAt < :endDate
    """)
    fun sumCashOutByUserIdAndPeriod(
        userId: UUID,
        startDate: OffsetDateTime,
        endDate: OffsetDateTime
    ): Long
}
