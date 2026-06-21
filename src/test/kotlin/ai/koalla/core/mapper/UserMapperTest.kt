package ai.koalla.core.mapper

import ai.koalla.core.domain.User
import ai.koalla.core.entity.UserEntity
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.UUID

class UserMapperTest {

    @Test
    fun `toDomain should map UserEntity to User correctly`() {
        // Given
        val now = OffsetDateTime.now()
        val id = UUID.randomUUID()
        val entity = UserEntity(
            waId = "5511999999999",
            fullName = "Test User",
            email = "test@example.com",
            planType = "STARTER",
            lifetime = false,
            isActive = true
        ).apply {
            this.id = id
            this.createdAt = now
            this.updateAt = now
        }

        // When
        val domain = entity.toDomain()

        // Then
        domain.id shouldBeEqualTo id
        domain.waId shouldBeEqualTo "5511999999999"
        domain.fullName shouldBeEqualTo "Test User"
        domain.email shouldBeEqualTo "test@example.com"
        domain.planType shouldBeEqualTo "STARTER"
        domain.lifetime shouldBeEqualTo false
        domain.isActive shouldBeEqualTo true
        domain.createdAt shouldBeEqualTo now
        domain.updatedAt shouldBeEqualTo now
    }

    @Test
    fun `toDomain should handle null optional fields`() {
        // Given
        val id = UUID.randomUUID()
        val entity = UserEntity(waId = "5511888888888").apply {
            this.id = id
        }

        // When
        val domain = entity.toDomain()

        // Then
        domain.waId shouldBeEqualTo "5511888888888"
        domain.fullName shouldBeEqualTo null
        domain.email shouldBeEqualTo null
        domain.planType shouldBeEqualTo "FREE"
    }

    @Test
    fun `toResponse should map User to UserResponse correctly`() {
        // Given
        val now = OffsetDateTime.now()
        val user = User(
            id = UUID.randomUUID(),
            waId = "5511999999999",
            fullName = "Test User",
            email = "test@example.com",
            planType = "PRO",
            lifetime = true,
            isActive = true,
            createdAt = now,
            updatedAt = now
        )

        // When
        val response = user.toResponse()

        // Then
        response.id shouldBeEqualTo user.id
        response.waId shouldBeEqualTo user.waId
        response.fullName shouldBeEqualTo user.fullName
        response.email shouldBeEqualTo user.email
        response.planType shouldBeEqualTo user.planType
        response.lifetime shouldBeEqualTo user.lifetime
        response.isActive shouldBeEqualTo user.isActive
        response.createdAt shouldBeEqualTo now
        response.updateAt shouldBeEqualTo now
    }
}

