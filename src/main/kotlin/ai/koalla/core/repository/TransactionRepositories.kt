package ai.koalla.core.repository

import ai.koalla.core.domain.MovementType
import ai.koalla.core.entity.CategoryEntity
import ai.koalla.core.entity.TransactionEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.UUID

@Repository
interface CategoryRepository : JpaRepository<CategoryEntity, Int> {
    fun findByUserIdIsNull(): List<CategoryEntity>
}

@Repository
interface TransactionRepository : JpaRepository<TransactionEntity, UUID> {

    fun findByUserIdOrderByOccurredAtDesc(userId: UUID, pageable: Pageable): List<TransactionEntity>


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
        AND t.movement = :movement
        AND t.occurredAt >= :startDate
        AND t.occurredAt < :endDate
    """)
    fun sumByMovementAndPeriod(
        @Param("userId") userId: UUID,
        @Param("movement") movement: MovementType,
        @Param("startDate") startDate: OffsetDateTime,
        @Param("endDate") endDate: OffsetDateTime
    ): Long

    fun sumCashInByUserIdAndPeriod(userId: UUID, startDate: OffsetDateTime, endDate: OffsetDateTime): Long =
        sumByMovementAndPeriod(userId, MovementType.CASH_IN, startDate, endDate)

    fun sumCashOutByUserIdAndPeriod(userId: UUID, startDate: OffsetDateTime, endDate: OffsetDateTime): Long =
        sumByMovementAndPeriod(userId, MovementType.CASH_OUT, startDate, endDate)
}
