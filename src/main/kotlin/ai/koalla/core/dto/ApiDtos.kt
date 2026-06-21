package ai.koalla.core.dto

import ai.koalla.core.domain.EntityContext
import ai.koalla.core.domain.MovementType
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.time.OffsetDateTime
import java.util.UUID

// ===================== Transaction DTOs =====================

data class TransactionCreateRequest(
    @field:NotNull
    val userId: UUID,
    val description: String? = null,
    @field:Positive
    val amount: Long, // in cents
    @field:NotNull
    val movement: MovementType,
    val categoryId: Int? = null,
    val entityType: EntityContext = EntityContext.PF,
    val source: String = "whatsapp",
    val occurredAt: OffsetDateTime? = null,
)

data class TransactionResponse(
    val id: UUID,
    val userId: UUID,
    val description: String?,
    val amount: Long,
    val movement: MovementType,
    val categoryId: Int?,
    val entityType: EntityContext,
    val source: String,
    val occurredAt: OffsetDateTime?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

data class TransactionSummary(
    val totalCashIn: Long,
    val totalCashOut: Long,
    val balance: Long,
    val byCategory: Map<String, Long>,
)

// ===================== User DTOs =====================

data class UserResponse(
    val id: UUID,
    val waId: String,
    val fullName: String?,
    val email: String?,
    val planType: String,
    val lifetime: Boolean,
    val isActive: Boolean,
    val createdAt: OffsetDateTime,
    val updateAt: OffsetDateTime,
)

data class UserDeactivateResponse(
    val id: UUID,
    val isActive: Boolean,
    val message: String,
)
