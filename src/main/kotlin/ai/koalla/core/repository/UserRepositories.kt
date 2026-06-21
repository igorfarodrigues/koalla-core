package ai.koalla.core.repository

import ai.koalla.core.entity.AuthEntity
import ai.koalla.core.entity.UserEntity
import ai.koalla.core.entity.UserStateEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface UserRepository : JpaRepository<UserEntity, UUID> {
    fun findByWaId(waId: String): UserEntity?
    fun findByEmail(email: String): UserEntity?
}

@Repository
interface AuthRepository : JpaRepository<AuthEntity, UUID> {
    fun findByUserId(userId: UUID): AuthEntity?
    fun findByMagicLinkToken(token: String): AuthEntity?
}

@Repository
interface UserStateRepository : JpaRepository<UserStateEntity, UUID>
