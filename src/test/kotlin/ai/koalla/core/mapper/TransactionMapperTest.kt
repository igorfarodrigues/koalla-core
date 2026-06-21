package ai.koalla.core.mapper

import ai.koalla.core.domain.EntityContext
import ai.koalla.core.domain.MovementType
import ai.koalla.core.domain.Transaction
import ai.koalla.core.entity.TransactionEntity
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.UUID

class TransactionMapperTest {

    @Test
    fun `toDomain should map TransactionEntity to Transaction correctly`() {
        // Given
        val now = OffsetDateTime.now()
        val id = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val entity = TransactionEntity(
            userId = userId,
            description = "Almoço",
            amount = 4500L,
            movement = MovementType.CASH_OUT,
            categoryId = 1,
            entityType = EntityContext.PF,
            source = "whatsapp",
            externalId = "ext-123",
            occurredAt = now
        ).apply {
            this.id = id
            this.createdAt = now
            this.updatedAt = now
        }

        // When
        val domain = entity.toDomain()

        // Then
        domain.id shouldBeEqualTo id
        domain.userId shouldBeEqualTo userId
        domain.description shouldBeEqualTo "Almoço"
        domain.amount shouldBeEqualTo 4500L
        domain.movement shouldBeEqualTo MovementType.CASH_OUT
        domain.categoryId shouldBeEqualTo 1
        domain.entityType shouldBeEqualTo EntityContext.PF
        domain.source shouldBeEqualTo "whatsapp"
        domain.externalId shouldBeEqualTo "ext-123"
        domain.occurredAt shouldBeEqualTo now
        domain.createdAt shouldBeEqualTo now
        domain.updatedAt shouldBeEqualTo now
    }

    @Test
    fun `toDomain should handle null optional fields`() {
        // Given
        val userId = UUID.randomUUID()
        val id = UUID.randomUUID()
        val entity = TransactionEntity(
            userId = userId,
            amount = 1000L,
            movement = MovementType.CASH_IN
        ).apply {
            this.id = id
        }

        // When
        val domain = entity.toDomain()

        // Then
        domain.description shouldBeEqualTo null
        domain.categoryId shouldBeEqualTo null
        domain.externalId shouldBeEqualTo null
        domain.occurredAt shouldBeEqualTo null
    }

    @Test
    fun `toResponse should map Transaction to TransactionResponse correctly`() {
        // Given
        val now = OffsetDateTime.now()
        val transaction = Transaction(
            id = UUID.randomUUID(),
            userId = UUID.randomUUID(),
            description = "Salário",
            amount = 500000L,
            movement = MovementType.CASH_IN,
            categoryId = 10,
            entityType = EntityContext.PJ,
            source = "api",
            externalId = "salary-june",
            occurredAt = now,
            createdAt = now,
            updatedAt = now
        )

        // When
        val response = transaction.toResponse()

        // Then
        response.id shouldBeEqualTo transaction.id
        response.userId shouldBeEqualTo transaction.userId
        response.description shouldBeEqualTo transaction.description
        response.amount shouldBeEqualTo transaction.amount
        response.movement shouldBeEqualTo transaction.movement
        response.categoryId shouldBeEqualTo transaction.categoryId
        response.entityType shouldBeEqualTo transaction.entityType
        response.source shouldBeEqualTo transaction.source
        response.occurredAt shouldBeEqualTo transaction.occurredAt
        response.createdAt shouldBeEqualTo transaction.createdAt
        response.updatedAt shouldBeEqualTo transaction.updatedAt
    }

    @Test
    fun `toDomain should map CASH_IN movement correctly`() {
        // Given
        val entity = TransactionEntity(
            userId = UUID.randomUUID(),
            amount = 10000L,
            movement = MovementType.CASH_IN
        ).apply { id = UUID.randomUUID() }

        // When
        val domain = entity.toDomain()

        // Then
        domain.movement shouldBeEqualTo MovementType.CASH_IN
    }

    @Test
    fun `toDomain should map CASH_OUT movement correctly`() {
        // Given
        val entity = TransactionEntity(
            userId = UUID.randomUUID(),
            amount = 5000L,
            movement = MovementType.CASH_OUT
        ).apply { id = UUID.randomUUID() }

        // When
        val domain = entity.toDomain()

        // Then
        domain.movement shouldBeEqualTo MovementType.CASH_OUT
    }
}

