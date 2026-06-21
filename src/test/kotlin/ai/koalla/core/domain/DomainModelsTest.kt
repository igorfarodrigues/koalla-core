package ai.koalla.core.domain

import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.UUID

class DomainModelsTest {
    @Nested
    inner class UserTests {
        @Test
        fun `should create User with all fields`() {
            val now = OffsetDateTime.now()
            val id = UUID.randomUUID()

            val user =
                User(
                    id = id,
                    waId = "5511999999999",
                    fullName = "Test User",
                    email = "test@example.com",
                    planType = "PRO",
                    lifetime = false,
                    isActive = true,
                    createdAt = now,
                    updatedAt = now,
                )

            user.id shouldBeEqualTo id
            user.waId shouldBeEqualTo "5511999999999"
            user.fullName shouldBeEqualTo "Test User"
            user.email shouldBeEqualTo "test@example.com"
            user.planType shouldBeEqualTo "PRO"
            user.lifetime shouldBeEqualTo false
            user.isActive shouldBeEqualTo true
            user.createdAt shouldBeEqualTo now
            user.updatedAt shouldBeEqualTo now
        }

        @Test
        fun `should support equality comparison`() {
            val now = OffsetDateTime.now()
            val id = UUID.randomUUID()

            val user1 = User(id, "123", "Name", "email@test.com", "FREE", false, true, now, now)
            val user2 = User(id, "123", "Name", "email@test.com", "FREE", false, true, now, now)
            val user3 = User(UUID.randomUUID(), "123", "Name", "email@test.com", "FREE", false, true, now, now)

            user1 shouldBeEqualTo user2
            user1 shouldNotBeEqualTo user3
        }

        @Test
        fun `should support copy with modifications`() {
            val now = OffsetDateTime.now()
            val user =
                User(
                    id = UUID.randomUUID(),
                    waId = "123",
                    fullName = "Original",
                    email = null,
                    planType = "FREE",
                    lifetime = false,
                    isActive = true,
                    createdAt = now,
                    updatedAt = now,
                )

            val upgraded = user.copy(planType = "PRO", lifetime = true)

            upgraded.planType shouldBeEqualTo "PRO"
            upgraded.lifetime shouldBeEqualTo true
            upgraded.waId shouldBeEqualTo user.waId
        }
    }

    @Nested
    inner class TransactionTests {
        @Test
        fun `should create Transaction with all fields`() {
            val now = OffsetDateTime.now()
            val id = UUID.randomUUID()
            val userId = UUID.randomUUID()

            val transaction =
                Transaction(
                    id = id,
                    userId = userId,
                    description = "Test purchase",
                    amount = 5000L,
                    movement = MovementType.CASH_OUT,
                    categoryId = 1,
                    entityType = EntityContext.PF,
                    source = "whatsapp",
                    externalId = "ext-123",
                    occurredAt = now,
                    createdAt = now,
                    updatedAt = now,
                )

            transaction.id shouldBeEqualTo id
            transaction.userId shouldBeEqualTo userId
            transaction.description shouldBeEqualTo "Test purchase"
            transaction.amount shouldBeEqualTo 5000L
            transaction.movement shouldBeEqualTo MovementType.CASH_OUT
            transaction.categoryId shouldBeEqualTo 1
            transaction.entityType shouldBeEqualTo EntityContext.PF
            transaction.source shouldBeEqualTo "whatsapp"
            transaction.externalId shouldBeEqualTo "ext-123"
        }

        @Test
        fun `should allow null optional fields`() {
            val now = OffsetDateTime.now()
            val transaction =
                Transaction(
                    id = UUID.randomUUID(),
                    userId = UUID.randomUUID(),
                    description = null,
                    amount = 1000L,
                    movement = MovementType.CASH_IN,
                    categoryId = null,
                    entityType = EntityContext.PF,
                    source = "api",
                    externalId = null,
                    occurredAt = null,
                    createdAt = now,
                    updatedAt = now,
                )

            transaction.description shouldBeEqualTo null
            transaction.categoryId shouldBeEqualTo null
            transaction.externalId shouldBeEqualTo null
            transaction.occurredAt shouldBeEqualTo null
        }
    }

    @Nested
    inner class AgentContextTests {
        @Test
        fun `should create AgentContext with all fields`() {
            val userId = UUID.randomUUID()

            val context =
                AgentContext(
                    userId = userId,
                    waId = "5511999999999",
                    accountId = 1,
                    conversationId = 100,
                    contactId = 50,
                    messageId = 500,
                    contactName = "John Doe",
                    labels = listOf("vip", "active"),
                    contactCustomAttributes = mapOf("plan" to "PRO"),
                    alertConversationId = "200",
                )

            context.userId shouldBeEqualTo userId
            context.waId shouldBeEqualTo "5511999999999"
            context.accountId shouldBeEqualTo 1
            context.conversationId shouldBeEqualTo 100
            context.contactId shouldBeEqualTo 50
            context.messageId shouldBeEqualTo 500
            context.contactName shouldBeEqualTo "John Doe"
            context.labels shouldBeEqualTo listOf("vip", "active")
            context.contactCustomAttributes shouldBeEqualTo mapOf("plan" to "PRO")
            context.alertConversationId shouldBeEqualTo "200"
        }

        @Test
        fun `should be immutable`() {
            val labels = mutableListOf("label1")
            val attrs = mutableMapOf<String, Any>("key" to "value")

            val context =
                AgentContext(
                    userId = UUID.randomUUID(),
                    waId = "123",
                    accountId = 1,
                    conversationId = 1,
                    contactId = 1,
                    messageId = 1,
                    contactName = "Test",
                    labels = labels,
                    contactCustomAttributes = attrs,
                    alertConversationId = "1",
                )

            // Original context should have initial values
            context.labels shouldBeEqualTo listOf("label1")
        }
    }

    @Nested
    inner class MovementTypeTests {
        @Test
        fun `should have CASH_IN and CASH_OUT values`() {
            val cashIn = MovementType.CASH_IN
            val cashOut = MovementType.CASH_OUT

            cashIn.name shouldBeEqualTo "CASH_IN"
            cashOut.name shouldBeEqualTo "CASH_OUT"
        }

        @Test
        fun `should have exactly 2 values`() {
            MovementType.entries.size shouldBeEqualTo 2
        }
    }

    @Nested
    inner class EntityContextTests {
        @Test
        fun `should have PF and PJ values`() {
            val pf = EntityContext.PF
            val pj = EntityContext.PJ

            pf.name shouldBeEqualTo "PF"
            pj.name shouldBeEqualTo "PJ"
        }

        @Test
        fun `should have exactly 2 values`() {
            EntityContext.entries.size shouldBeEqualTo 2
        }
    }

    @Nested
    inner class SubscriptionStatusTests {
        @Test
        fun `should have all subscription status values`() {
            val statuses = SubscriptionStatus.entries

            statuses.map { it.name } shouldBeEqualTo
                listOf(
                    "TRIALING",
                    "ACTIVE",
                    "PAST_DUE",
                    "CANCELED",
                    "EXPIRED",
                )
        }

        @Test
        fun `should have exactly 5 values`() {
            SubscriptionStatus.entries.size shouldBeEqualTo 5
        }
    }
}
