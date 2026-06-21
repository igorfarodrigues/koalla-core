package ai.koalla.core.domain

import java.time.OffsetDateTime
import java.util.UUID

/**
 * Pure domain model for a Koalla user.
 * No JPA annotations — safe to use across all layers.
 */
data class User(
    val id: UUID,
    val waId: String,
    val fullName: String?,
    val email: String?,
    val planType: String,
    val lifetime: Boolean,
    val isActive: Boolean,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
)
