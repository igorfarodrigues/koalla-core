package ai.koalla.core.repository

import ai.koalla.core.entity.Category
import ai.koalla.core.entity.CategoryKeyword
import ai.koalla.core.entity.Transaction
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.UUID

@Repository
interface CategoryRepository : JpaRepository<Category, Int> {
    fun findByUserId(userId: UUID): List<Category>
    fun findByUserIdIsNull(): List<Category>
}

@Repository
interface CategoryKeywordRepository : JpaRepository<CategoryKeyword, Int> {
    fun findByCategoryId(categoryId: Int): List<CategoryKeyword>
}

@Repository
interface TransactionRepository : JpaRepository<Transaction, UUID> {

    fun findByUserIdOrderByOccurredAtDesc(userId: UUID, pageable: Pageable): List<Transaction>

    fun findByUserIdOrderByCreatedAtDesc(userId: UUID, pageable: Pageable): List<Transaction>

    @Query("""
        SELECT t FROM Transaction t 
        WHERE t.userId = :userId 
        AND t.occurredAt >= :startDate 
        AND t.occurredAt < :endDate
        ORDER BY t.occurredAt DESC
    """)
    fun findByUserIdAndPeriod(
        userId: UUID,
        startDate: OffsetDateTime,
        endDate: OffsetDateTime
    ): List<Transaction>

    @Query("""
        SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t 
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
        SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t 
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

