package ai.koalla.core.domain

import ai.koalla.core.entity.EntityContext
import ai.koalla.core.entity.MovementType
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Pure domain model for a financial transaction.
 * No JPA annotations — safe to use across all layers.
 */
data class Transaction(
    val id: UUID,
    val userId: UUID,
    val description: String?,
    val amount: Long,
    val movement: MovementType,
    val categoryId: Int?,
    val entityType: EntityContext,
    val source: String,
    val externalId: String?,
    val occurredAt: OffsetDateTime?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
)
