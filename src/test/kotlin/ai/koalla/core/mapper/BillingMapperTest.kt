package ai.koalla.core.mapper

import ai.koalla.core.domain.SubscriptionStatus
import ai.koalla.core.entity.AsaasCustomer
import ai.koalla.core.entity.Invoice
import ai.koalla.core.entity.SubStatus
import ai.koalla.core.entity.Subscription
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

class BillingMapperTest {

    @Test
    fun `Subscription toDomain should map all fields correctly`() {
        // Given
        val now = OffsetDateTime.now()
        val id = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val nextDue = LocalDate.now().plusDays(30)
        val graceExpires = now.plusHours(48)

        val entity = Subscription(
            userId = userId,
            asaasSubscriptionId = "sub_123",
            status = SubStatus.ACTIVE,
            planName = "PRO",
            nextDueDate = nextDue,
            graceExpiresAt = graceExpires
        ).apply {
            this.id = id
            this.createdAt = now
        }

        // When
        val domain = entity.toDomain()

        // Then
        domain.id shouldBeEqualTo id
        domain.userId shouldBeEqualTo userId
        domain.asaasSubscriptionId shouldBeEqualTo "sub_123"
        domain.status shouldBeEqualTo SubscriptionStatus.ACTIVE
        domain.planName shouldBeEqualTo "PRO"
        domain.nextDueDate shouldBeEqualTo nextDue
        domain.graceExpiresAt shouldBeEqualTo graceExpires
        domain.createdAt shouldBeEqualTo now
    }

    @Test
    fun `Invoice toDomain should map all fields correctly`() {
        // Given
        val now = OffsetDateTime.now()
        val id = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val subscriptionId = UUID.randomUUID()

        val entity = Invoice(
            userId = userId,
            subscriptionId = subscriptionId,
            asaasPaymentId = "pay_456",
            amount = BigDecimal("49.90"),
            status = "CONFIRMED",
            pixCode = "pix-code-123",
            paymentLink = "https://pay.asaas.com/123"
        ).apply {
            this.id = id
            this.createdAt = now
        }

        // When
        val domain = entity.toDomain()

        // Then
        domain.id shouldBeEqualTo id
        domain.userId shouldBeEqualTo userId
        domain.subscriptionId shouldBeEqualTo subscriptionId
        domain.asaasPaymentId shouldBeEqualTo "pay_456"
        domain.amount shouldBeEqualTo BigDecimal("49.90")
        domain.status shouldBeEqualTo "CONFIRMED"
        domain.pixCode shouldBeEqualTo "pix-code-123"
        domain.paymentLink shouldBeEqualTo "https://pay.asaas.com/123"
        domain.createdAt shouldBeEqualTo now
    }

    @Test
    fun `AsaasCustomer toDomain should map all fields correctly`() {
        // Given
        val now = OffsetDateTime.now()
        val userId = UUID.randomUUID()

        val entity = AsaasCustomer(
            userId = userId,
            asaasCustomerId = "cus_789"
        ).apply {
            this.createdAt = now
        }

        // When
        val domain = entity.toDomain()

        // Then
        domain.userId shouldBeEqualTo userId
        domain.asaasCustomerId shouldBeEqualTo "cus_789"
        domain.createdAt shouldBeEqualTo now
    }

    @ParameterizedTest
    @EnumSource(SubStatus::class)
    fun `SubStatus toDomain should map all enum values correctly`(entityStatus: SubStatus) {
        // When
        val domainStatus = entityStatus.toDomain()

        // Then
        domainStatus.name shouldBeEqualTo entityStatus.name
    }

    @ParameterizedTest
    @EnumSource(SubscriptionStatus::class)
    fun `SubscriptionStatus toEntity should map all enum values correctly`(domainStatus: SubscriptionStatus) {
        // When
        val entityStatus = domainStatus.toEntity()

        // Then
        entityStatus.name shouldBeEqualTo domainStatus.name
    }

    @Test
    fun `SubStatus TRIALING maps to SubscriptionStatus TRIALING`() {
        SubStatus.TRIALING.toDomain() shouldBeEqualTo SubscriptionStatus.TRIALING
    }

    @Test
    fun `SubStatus ACTIVE maps to SubscriptionStatus ACTIVE`() {
        SubStatus.ACTIVE.toDomain() shouldBeEqualTo SubscriptionStatus.ACTIVE
    }

    @Test
    fun `SubStatus PAST_DUE maps to SubscriptionStatus PAST_DUE`() {
        SubStatus.PAST_DUE.toDomain() shouldBeEqualTo SubscriptionStatus.PAST_DUE
    }

    @Test
    fun `SubStatus CANCELED maps to SubscriptionStatus CANCELED`() {
        SubStatus.CANCELED.toDomain() shouldBeEqualTo SubscriptionStatus.CANCELED
    }

    @Test
    fun `SubStatus EXPIRED maps to SubscriptionStatus EXPIRED`() {
        SubStatus.EXPIRED.toDomain() shouldBeEqualTo SubscriptionStatus.EXPIRED
    }

    @Test
    fun `toDomain and toEntity should be inverse operations`() {
        // Given
        val originalStatus = SubStatus.ACTIVE

        // When
        val domainStatus = originalStatus.toDomain()
        val backToEntity = domainStatus.toEntity()

        // Then
        backToEntity shouldBeEqualTo originalStatus
    }
}

