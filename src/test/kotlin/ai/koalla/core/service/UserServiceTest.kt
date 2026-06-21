package ai.koalla.core.service

import ai.koalla.core.entity.UserEntity
import ai.koalla.core.exception.UserNotFoundException
import ai.koalla.core.repository.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeNull
import org.amshove.kluent.shouldThrow
import org.amshove.kluent.withMessage
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.UUID

class UserServiceTest {
    private lateinit var userRepository: UserRepository
    private lateinit var userService: UserService

    @BeforeEach
    fun setup() {
        userRepository = mockk()
        userService = UserService(userRepository)
    }

    @Nested
    inner class FindByWaId {
        @Test
        fun `should return user when found`() {
            // Given
            val waId = "5511999999999"
            val entity = createUserEntity(waId = waId)
            every { userRepository.findByWaId(waId) } returns entity

            // When
            val result = userService.findByWaId(waId)

            // Then
            result.shouldNotBeNull()
            result.waId shouldBeEqualTo waId
        }

        @Test
        fun `should return null when not found`() {
            // Given
            val waId = "5511999999999"
            every { userRepository.findByWaId(waId) } returns null

            // When
            val result = userService.findByWaId(waId)

            // Then
            result shouldBeEqualTo null
        }
    }

    @Nested
    inner class FindById {
        @Test
        fun `should return user when found`() {
            // Given
            val id = UUID.randomUUID()
            val entity = createUserEntity(id = id)
            every { userRepository.findById(id) } returns Optional.of(entity)

            // When
            val result = userService.findById(id)

            // Then
            result.shouldNotBeNull()
            result.id shouldBeEqualTo id
        }

        @Test
        fun `should return null when not found`() {
            // Given
            val id = UUID.randomUUID()
            every { userRepository.findById(id) } returns Optional.empty()

            // When
            val result = userService.findById(id)

            // Then
            result shouldBeEqualTo null
        }
    }

    @Nested
    inner class FindByEmail {
        @Test
        fun `should return user when found`() {
            // Given
            val email = "test@example.com"
            val entity = createUserEntity(email = email)
            every { userRepository.findByEmail(email) } returns entity

            // When
            val result = userService.findByEmail(email)

            // Then
            result.shouldNotBeNull()
            result.email shouldBeEqualTo email
        }

        @Test
        fun `should return null when not found`() {
            // Given
            val email = "test@example.com"
            every { userRepository.findByEmail(email) } returns null

            // When
            val result = userService.findByEmail(email)

            // Then
            result shouldBeEqualTo null
        }
    }

    @Nested
    inner class CreateUser {
        @Test
        fun `should create user with all parameters`() {
            // Given
            val slot = slot<UserEntity>()
            every { userRepository.save(capture(slot)) } answers {
                slot.captured.apply { id = UUID.randomUUID() }
            }

            // When
            val result =
                userService.createUser(
                    waId = "5511999999999",
                    fullName = "Test User",
                    email = "test@example.com",
                    planType = "PRO",
                    isActive = true,
                )

            // Then
            result.shouldNotBeNull()
            result.waId shouldBeEqualTo "5511999999999"
            result.fullName shouldBeEqualTo "Test User"
            result.email shouldBeEqualTo "test@example.com"
            result.planType shouldBeEqualTo "PRO"
            result.isActive shouldBeEqualTo true
        }

        @Test
        fun `should create user with default values`() {
            // Given
            val slot = slot<UserEntity>()
            every { userRepository.save(capture(slot)) } answers {
                slot.captured.apply { id = UUID.randomUUID() }
            }

            // When
            val result = userService.createUser(waId = "5511999999999")

            // Then
            result.shouldNotBeNull()
            result.waId shouldBeEqualTo "5511999999999"
            result.fullName shouldBeEqualTo null
            result.email shouldBeEqualTo null
            result.planType shouldBeEqualTo "FREE"
            result.isActive shouldBeEqualTo true
        }
    }

    @Nested
    inner class GetOrCreateByWaId {
        @Test
        fun `should return existing user`() {
            // Given
            val waId = "5511999999999"
            val entity = createUserEntity(waId = waId)
            every { userRepository.findByWaId(waId) } returns entity

            // When
            val result = userService.getOrCreateByWaId(waId)

            // Then
            result.waId shouldBeEqualTo waId
            verify(exactly = 0) { userRepository.save(any()) }
        }

        @Test
        fun `should create new user when not found`() {
            // Given
            val waId = "5511999999999"
            val slot = slot<UserEntity>()
            every { userRepository.findByWaId(waId) } returns null
            every { userRepository.save(capture(slot)) } answers {
                slot.captured.apply { id = UUID.randomUUID() }
            }

            // When
            val result = userService.getOrCreateByWaId(waId, "New User")

            // Then
            result.waId shouldBeEqualTo waId
            result.fullName shouldBeEqualTo "New User"
            verify(exactly = 1) { userRepository.save(any()) }
        }
    }

    @Nested
    inner class Deactivate {
        @Test
        fun `should deactivate user`() {
            // Given
            val id = UUID.randomUUID()
            val entity = createUserEntity(id = id, isActive = true)
            every { userRepository.findById(id) } returns Optional.of(entity)
            every { userRepository.save(any()) } answers { firstArg() }

            // When
            val result = userService.deactivate(id)

            // Then
            result.isActive shouldBeEqualTo false
            verify { userRepository.save(match { !it.isActive }) }
        }

        @Test
        fun `should throw when user not found`() {
            // Given
            val id = UUID.randomUUID()
            every { userRepository.findById(id) } returns Optional.empty()

            // When/Then
            val exception = { userService.deactivate(id) }
            exception shouldThrow UserNotFoundException::class withMessage "User not found: $id"
        }
    }

    @Nested
    inner class Activate {
        @Test
        fun `should activate user`() {
            // Given
            val id = UUID.randomUUID()
            val entity = createUserEntity(id = id, isActive = false)
            every { userRepository.findById(id) } returns Optional.of(entity)
            every { userRepository.save(any()) } answers { firstArg() }

            // When
            val result = userService.activate(id)

            // Then
            result.isActive shouldBeEqualTo true
            verify { userRepository.save(match { it.isActive }) }
        }

        @Test
        fun `should throw when user not found`() {
            // Given
            val id = UUID.randomUUID()
            every { userRepository.findById(id) } returns Optional.empty()

            // When/Then
            val exception = { userService.activate(id) }
            exception shouldThrow UserNotFoundException::class withMessage "User not found: $id"
        }
    }

    @Nested
    inner class UpdatePlan {
        @Test
        fun `should update plan to uppercase`() {
            // Given
            val id = UUID.randomUUID()
            val entity = createUserEntity(id = id, planType = "FREE")
            every { userRepository.findById(id) } returns Optional.of(entity)
            every { userRepository.save(any()) } answers { firstArg() }

            // When
            val result = userService.updatePlan(id, "pro")

            // Then
            result.planType shouldBeEqualTo "PRO"
            verify { userRepository.save(match { it.planType == "PRO" }) }
        }

        @Test
        fun `should throw when user not found`() {
            // Given
            val id = UUID.randomUUID()
            every { userRepository.findById(id) } returns Optional.empty()

            // When/Then
            val exception = { userService.updatePlan(id, "PRO") }
            exception shouldThrow UserNotFoundException::class withMessage "User not found: $id"
        }
    }

    private fun createUserEntity(
        id: UUID = UUID.randomUUID(),
        waId: String = "5511999999999",
        fullName: String? = "Test User",
        email: String? = "test@example.com",
        planType: String = "FREE",
        isActive: Boolean = true,
    ): UserEntity =
        UserEntity(
            waId = waId,
            fullName = fullName,
            email = email,
            planType = planType,
            isActive = isActive,
        ).apply { this.id = id }
}
