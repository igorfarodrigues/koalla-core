package ai.koalla.core.repository

import ai.koalla.core.entity.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface UserRepository : JpaRepository<User, UUID> {
    fun findByWaId(waId: String): User?
    fun findByEmail(email: String): User?
}

@Repository
interface AuthRepository : JpaRepository<Auth, UUID> {
    fun findByUserId(userId: UUID): Auth?
    fun findByMagicLinkToken(token: String): Auth?
}

@Repository
interface UserStateRepository : JpaRepository<UserState, UUID>

