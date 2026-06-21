package ai.koalla.core.mapper

import ai.koalla.core.domain.User
import ai.koalla.core.dto.UserResponse
import ai.koalla.core.entity.UserEntity

// ── Entity → Domain ──────────────────────────────────────────────────────────

fun UserEntity.toDomain(): User =
    User(
        id = id!!,
        waId = waId,
        fullName = fullName,
        email = email,
        planType = planType,
        lifetime = lifetime,
        isActive = isActive,
        createdAt = createdAt,
        updatedAt = updateAt, // entity field is "updateAt" (typo preserved for DB compat)
    )

// ── Domain → Response DTO ─────────────────────────────────────────────────────

fun User.toResponse(): UserResponse =
    UserResponse(
        id = id,
        waId = waId,
        fullName = fullName,
        email = email,
        planType = planType,
        lifetime = lifetime,
        isActive = isActive,
        createdAt = createdAt,
        updateAt = updatedAt, // DTO field is "updateAt" (kept for API compat)
    )
