package ai.koalla.core.mapper

import ai.koalla.core.domain.Transaction
import ai.koalla.core.dto.TransactionResponse
import ai.koalla.core.entity.TransactionEntity

// ── Entity → Domain ──────────────────────────────────────────────────────────

fun TransactionEntity.toDomain(): Transaction =
    Transaction(
        id = id!!,
        userId = userId,
        description = description,
        amount = amount,
        movement = movement,
        categoryId = categoryId,
        entityType = entityType,
        source = source,
        externalId = externalId,
        occurredAt = occurredAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

// ── Domain → Response DTO ─────────────────────────────────────────────────────

fun Transaction.toResponse(): TransactionResponse =
    TransactionResponse(
        id = id,
        userId = userId,
        description = description,
        amount = amount,
        movement = movement,
        categoryId = categoryId,
        entityType = entityType,
        source = source,
        occurredAt = occurredAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
