package ai.koalla.core.service

import ai.koalla.core.domain.User
import ai.koalla.core.entity.UserEntity
import ai.koalla.core.exception.UserNotFoundException
import ai.koalla.core.mapper.toDomain
import ai.koalla.core.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class UserService(
    private val userRepository: UserRepository
) {

    fun findByWaId(waId: String): User? =
        userRepository.findByWaId(waId)?.toDomain()

    fun findById(id: UUID): User? =
        userRepository.findById(id).orElse(null)?.toDomain()

    fun findByEmail(email: String): User? =
        userRepository.findByEmail(email)?.toDomain()

    @Transactional
    fun createUser(
        waId: String,
        fullName: String? = null,
        email: String? = null,
        planType: String = "FREE",
        isActive: Boolean = true
    ): User {
        val entity = UserEntity(
            waId = waId,
            fullName = fullName,
            email = email,
            planType = planType,
            isActive = isActive
        )
        return userRepository.save(entity).toDomain()
    }

    @Transactional
    fun getOrCreateByWaId(waId: String, fullName: String? = null): User =
        findByWaId(waId) ?: createUser(waId, fullName)

    @Transactional
    fun deactivate(userId: UUID): User {
        val entity = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException(userId.toString()) }
        entity.isActive = false
        return userRepository.save(entity).toDomain()
    }

    @Transactional
    fun activate(userId: UUID): User {
        val entity = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException(userId.toString()) }
        entity.isActive = true
        return userRepository.save(entity).toDomain()
    }

    @Transactional
    fun updatePlan(userId: UUID, planType: String): User {
        val entity = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException(userId.toString()) }
        entity.planType = planType.uppercase()
        return userRepository.save(entity).toDomain()
    }
}
