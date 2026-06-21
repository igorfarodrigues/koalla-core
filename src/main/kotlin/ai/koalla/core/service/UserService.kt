package ai.koalla.core.service

import ai.koalla.core.dto.UserDeactivateResponse
import ai.koalla.core.dto.UserResponse
import ai.koalla.core.entity.User
import ai.koalla.core.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class UserService(
    private val userRepository: UserRepository
) {

    fun findByWaId(waId: String): User? {
        return userRepository.findByWaId(waId)
    }

    fun findById(id: UUID): User? {
        return userRepository.findById(id).orElse(null)
    }

    fun findByEmail(email: String): User? {
        return userRepository.findByEmail(email)
    }

    @Transactional
    fun createUser(waId: String, fullName: String? = null, email: String? = null): User {
        val user = User(
            waId = waId,
            fullName = fullName,
            email = email
        )
        return userRepository.save(user)
    }

    @Transactional
    fun getOrCreateByWaId(waId: String, fullName: String? = null): User {
        return findByWaId(waId) ?: createUser(waId, fullName)
    }

    @Transactional
    fun deactivate(userId: UUID): UserDeactivateResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User not found: $userId") }

        user.isActive = false
        userRepository.save(user)

        return UserDeactivateResponse(
            id = user.id!!,
            isActive = false,
            message = "User deactivated successfully"
        )
    }

    @Transactional
    fun activate(userId: UUID): User {
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User not found: $userId") }

        user.isActive = true
        return userRepository.save(user)
    }

    @Transactional
    fun updatePlan(userId: UUID, planType: String): User {
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User not found: $userId") }

        user.planType = planType.uppercase()
        return userRepository.save(user)
    }

    fun toResponse(user: User): UserResponse {
        return UserResponse(
            id = user.id!!,
            waId = user.waId,
            fullName = user.fullName,
            email = user.email,
            planType = user.planType,
            lifetime = user.lifetime,
            isActive = user.isActive,
            createdAt = user.createdAt,
            updateAt = user.updateAt
        )
    }
}

