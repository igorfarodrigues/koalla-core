package ai.koalla.core.service

import ai.koalla.core.config.KoallaProperties
import ai.koalla.core.config.PlansProperties
import ai.koalla.core.domain.User
import ai.koalla.core.dto.CardData
import ai.koalla.core.dto.CardHolderInfo
import ai.koalla.core.entity.*
import ai.koalla.core.exception.SubscriptionNotFoundException
import ai.koalla.core.exception.UserNotFoundException
import ai.koalla.core.gateway.AsaasGateway
import ai.koalla.core.gateway.ChatwootGateway
import ai.koalla.core.repository.*
import io.mockk.*
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeNull
import org.amshove.kluent.shouldThrow
import org.amshove.kluent.withMessage
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.*

class BillingServiceTest {

    private lateinit var userRepository: UserRepository
    private lateinit var asaasCustomerRepository: AsaasCustomerRepository
    private lateinit var subscriptionRepository: SubscriptionRepository
    private lateinit var invoiceRepository: InvoiceRepository
    private lateinit var webhookEventRepository: WebhookEventRepository
    private lateinit var asaasGateway: AsaasGateway
    private lateinit var chatwootGateway: ChatwootGateway
    private lateinit var props: KoallaProperties
    private lateinit var billingService: BillingService

    @BeforeEach
    fun setup() {
        userRepository = mockk()
        asaasCustomerRepository = mockk()
        subscriptionRepository = mockk()
        invoiceRepository = mockk()
        webhookEventRepository = mockk()
        asaasGateway = mockk()
        chatwootGateway = mockk()
        props = KoallaProperties(
            trialDays = 15,
            graceHours = 48,
            plans = PlansProperties(
                starter = BigDecimal("29.90"),
                pro = BigDecimal("79.90"),
                business = BigDecimal("99.90")
            )
        )

        billingService = BillingService(
            userRepository = userRepository,
            asaasCustomerRepository = asaasCustomerRepository,
            subscriptionRepository = subscriptionRepository,
            invoiceRepository = invoiceRepository,
            webhookEventRepository = webhookEventRepository,
            asaasGateway = asaasGateway,
            chatwootGateway = chatwootGateway,
            props = props
        )
    }

    @Nested
    inner class HandlePaymentConfirmedTests {
        @Test
        fun `should do nothing when subscriptionId is null`() {
            billingService.handlePaymentConfirmed("payment-123", null)

            verify(exactly = 0) { subscriptionRepository.findByAsaasSubscriptionId(any()) }
        }

        @Test
        fun `should do nothing when subscription not found`() {
            every { subscriptionRepository.findByAsaasSubscriptionId("sub-123") } returns null

            billingService.handlePaymentConfirmed("payment-123", "sub-123")

            verify(exactly = 0) { invoiceRepository.save(any()) }
        }

        @Test
        fun `should create new invoice when payment not found`() {
            val userId = UUID.randomUUID()
            val subscriptionId = UUID.randomUUID()
            val subscription = createSubscription(
                id = subscriptionId,
                userId = userId,
                status = SubStatus.TRIALING,
                planName = "STARTER"
            )

            every { subscriptionRepository.findByAsaasSubscriptionId("sub-123") } returns subscription
            every { invoiceRepository.findByAsaasPaymentId("payment-123") } returns null
            every { invoiceRepository.save(any()) } answers { firstArg() }
            every { subscriptionRepository.save(any()) } answers { firstArg() }
            every { userRepository.findById(userId) } returns Optional.empty()

            billingService.handlePaymentConfirmed("payment-123", "sub-123")

            verify {
                invoiceRepository.save(match {
                    it.asaasPaymentId == "payment-123" &&
                    it.status == "CONFIRMED" &&
                    it.amount == BigDecimal("29.90")
                })
            }
        }

        @Test
        fun `should update existing invoice status`() {
            val userId = UUID.randomUUID()
            val subscriptionId = UUID.randomUUID()
            val subscription = createSubscription(
                id = subscriptionId,
                userId = userId,
                status = SubStatus.PAST_DUE,
                planName = "STARTER"
            )
            val existingInvoice = Invoice(
                userId = userId,
                subscriptionId = subscriptionId,
                asaasPaymentId = "payment-123",
                amount = BigDecimal("29.90"),
                status = "PENDING"
            )

            every { subscriptionRepository.findByAsaasSubscriptionId("sub-123") } returns subscription
            every { invoiceRepository.findByAsaasPaymentId("payment-123") } returns existingInvoice
            every { invoiceRepository.save(any()) } answers { firstArg() }
            every { subscriptionRepository.save(any()) } answers { firstArg() }
            every { userRepository.findById(userId) } returns Optional.empty()

            billingService.handlePaymentConfirmed("payment-123", "sub-123")

            verify { invoiceRepository.save(match { it.status == "CONFIRMED" }) }
        }

        @Test
        fun `should update subscription status to ACTIVE and clear grace period`() {
            val userId = UUID.randomUUID()
            val subscriptionId = UUID.randomUUID()
            val subscription = createSubscription(
                id = subscriptionId,
                userId = userId,
                status = SubStatus.PAST_DUE,
                planName = "STARTER",
                graceExpiresAt = OffsetDateTime.now().plusHours(24)
            )

            every { subscriptionRepository.findByAsaasSubscriptionId("sub-123") } returns subscription
            every { invoiceRepository.findByAsaasPaymentId("payment-123") } returns null
            every { invoiceRepository.save(any()) } answers { firstArg() }
            every { subscriptionRepository.save(any()) } answers { firstArg() }
            every { userRepository.findById(userId) } returns Optional.empty()

            billingService.handlePaymentConfirmed("payment-123", "sub-123")

            verify {
                subscriptionRepository.save(match {
                    it.status == SubStatus.ACTIVE &&
                    it.graceExpiresAt == null
                })
            }
        }

        @Test
        fun `should activate inactive user`() {
            val userId = UUID.randomUUID()
            val subscriptionId = UUID.randomUUID()
            val subscription = createSubscription(
                id = subscriptionId,
                userId = userId,
                status = SubStatus.PAST_DUE,
                planName = "STARTER"
            )
            val userEntity = createUserEntity(id = userId, isActive = false)

            every { subscriptionRepository.findByAsaasSubscriptionId("sub-123") } returns subscription
            every { invoiceRepository.findByAsaasPaymentId("payment-123") } returns null
            every { invoiceRepository.save(any()) } answers { firstArg() }
            every { subscriptionRepository.save(any()) } answers { firstArg() }
            every { userRepository.findById(userId) } returns Optional.of(userEntity)
            every { userRepository.save(any()) } answers { firstArg() }

            billingService.handlePaymentConfirmed("payment-123", "sub-123")

            verify { userRepository.save(match { it.isActive }) }
        }

        @Test
        fun `should not save user if already active`() {
            val userId = UUID.randomUUID()
            val subscriptionId = UUID.randomUUID()
            val subscription = createSubscription(
                id = subscriptionId,
                userId = userId,
                status = SubStatus.TRIALING,
                planName = "STARTER"
            )
            val userEntity = createUserEntity(id = userId, isActive = true)

            every { subscriptionRepository.findByAsaasSubscriptionId("sub-123") } returns subscription
            every { invoiceRepository.findByAsaasPaymentId("payment-123") } returns null
            every { invoiceRepository.save(any()) } answers { firstArg() }
            every { subscriptionRepository.save(any()) } answers { firstArg() }
            every { userRepository.findById(userId) } returns Optional.of(userEntity)

            billingService.handlePaymentConfirmed("payment-123", "sub-123")

            verify(exactly = 0) { userRepository.save(any()) }
        }
    }

    @Nested
    inner class HandlePaymentOverdueTests {
        @Test
        fun `should do nothing when subscriptionId is null`() {
            billingService.handlePaymentOverdue("payment-123", null)

            verify(exactly = 0) { subscriptionRepository.findByAsaasSubscriptionId(any()) }
        }

        @Test
        fun `should do nothing when subscription not found`() {
            every { subscriptionRepository.findByAsaasSubscriptionId("sub-123") } returns null

            billingService.handlePaymentOverdue("payment-123", "sub-123")

            verify(exactly = 0) { invoiceRepository.save(any()) }
        }

        @Test
        fun `should set grace period when status changes to PAST_DUE`() {
            val userId = UUID.randomUUID()
            val subscriptionId = UUID.randomUUID()
            val subscription = createSubscription(
                id = subscriptionId,
                userId = userId,
                status = SubStatus.ACTIVE,
                planName = "STARTER"
            )
            val userEntity = createUserEntity(id = userId, waId = "5511999999999")

            every { subscriptionRepository.findByAsaasSubscriptionId("sub-123") } returns subscription
            every { subscriptionRepository.save(any()) } answers { firstArg() }
            every { userRepository.findById(userId) } returns Optional.of(userEntity)
            coEvery { chatwootGateway.sendMessageToPhone(any(), any()) } returns true
            every { invoiceRepository.findByAsaasPaymentId("payment-123") } returns null
            every { invoiceRepository.save(any()) } answers { firstArg() }

            billingService.handlePaymentOverdue("payment-123", "sub-123")

            verify {
                subscriptionRepository.save(match {
                    it.status == SubStatus.PAST_DUE &&
                    it.graceExpiresAt != null
                })
            }
        }

        @Test
        fun `should not change grace period if already PAST_DUE`() {
            val userId = UUID.randomUUID()
            val subscriptionId = UUID.randomUUID()
            val existingGraceExpiry = OffsetDateTime.now().plusHours(24)
            val subscription = createSubscription(
                id = subscriptionId,
                userId = userId,
                status = SubStatus.PAST_DUE,
                planName = "STARTER",
                graceExpiresAt = existingGraceExpiry
            )

            every { subscriptionRepository.findByAsaasSubscriptionId("sub-123") } returns subscription
            every { invoiceRepository.findByAsaasPaymentId("payment-123") } returns null
            every { invoiceRepository.save(any()) } answers { firstArg() }

            billingService.handlePaymentOverdue("payment-123", "sub-123")

            verify(exactly = 0) { subscriptionRepository.save(any()) }
        }

        @Test
        fun `should create overdue invoice`() {
            val userId = UUID.randomUUID()
            val subscriptionId = UUID.randomUUID()
            val subscription = createSubscription(
                id = subscriptionId,
                userId = userId,
                status = SubStatus.PAST_DUE,
                planName = "PRO"
            )

            every { subscriptionRepository.findByAsaasSubscriptionId("sub-123") } returns subscription
            every { invoiceRepository.findByAsaasPaymentId("payment-123") } returns null
            every { invoiceRepository.save(any()) } answers { firstArg() }

            billingService.handlePaymentOverdue("payment-123", "sub-123")

            verify {
                invoiceRepository.save(match {
                    it.status == "OVERDUE" &&
                    it.amount == BigDecimal("79.90")
                })
            }
        }
    }

    @Nested
    inner class HandleSubscriptionDeletedTests {
        @Test
        fun `should do nothing when subscription not found`() {
            every { subscriptionRepository.findByAsaasSubscriptionId("sub-123") } returns null

            billingService.handleSubscriptionDeleted("sub-123")

            verify(exactly = 0) { subscriptionRepository.save(any()) }
        }

        @Test
        fun `should cancel subscription and deactivate user`() {
            val userId = UUID.randomUUID()
            val subscriptionId = UUID.randomUUID()
            val subscription = createSubscription(
                id = subscriptionId,
                userId = userId,
                status = SubStatus.ACTIVE,
                planName = "STARTER"
            )
            val userEntity = createUserEntity(id = userId, waId = "5511999999999", isActive = true)

            every { subscriptionRepository.findByAsaasSubscriptionId("sub-123") } returns subscription
            every { subscriptionRepository.save(any()) } answers { firstArg() }
            every { userRepository.findById(userId) } returns Optional.of(userEntity)
            every { userRepository.save(any()) } answers { firstArg() }
            coEvery { chatwootGateway.sendMessageToPhone(any(), any()) } returns true

            billingService.handleSubscriptionDeleted("sub-123")

            verify { subscriptionRepository.save(match { it.status == SubStatus.CANCELED }) }
            verify { userRepository.save(match { !it.isActive }) }
        }

        @Test
        fun `should not deactivate already inactive user`() {
            val userId = UUID.randomUUID()
            val subscriptionId = UUID.randomUUID()
            val subscription = createSubscription(
                id = subscriptionId,
                userId = userId,
                status = SubStatus.ACTIVE,
                planName = "STARTER"
            )
            val userEntity = createUserEntity(id = userId, isActive = false)

            every { subscriptionRepository.findByAsaasSubscriptionId("sub-123") } returns subscription
            every { subscriptionRepository.save(any()) } answers { firstArg() }
            every { userRepository.findById(userId) } returns Optional.of(userEntity)

            billingService.handleSubscriptionDeleted("sub-123")

            verify { subscriptionRepository.save(match { it.status == SubStatus.CANCELED }) }
            verify(exactly = 0) { userRepository.save(any()) }
        }
    }

    @Nested
    inner class CancelUserSubscriptionTests {
        @Test
        fun `should throw when no active subscription found`() {
            val userId = UUID.randomUUID()
            val user = createUser(id = userId, waId = "5511999999999")

            every { subscriptionRepository.findActiveByUserId(userId) } returns null

            val exception = { billingService.cancelUserSubscription(user) }
            exception shouldThrow SubscriptionNotFoundException::class withMessage "No active subscription for user: $userId"
        }

        @Test
        fun `should throw when user not found`() {
            val userId = UUID.randomUUID()
            val user = createUser(id = userId, waId = "5511999999999")
            val subscription = createSubscription(
                userId = userId,
                status = SubStatus.ACTIVE,
                asaasSubscriptionId = "asaas-sub-123"
            )

            every { subscriptionRepository.findActiveByUserId(userId) } returns subscription
            coEvery { asaasGateway.cancelSubscription("asaas-sub-123") } returns emptyMap()
            every { subscriptionRepository.save(any()) } answers { firstArg() }
            every { userRepository.findById(userId) } returns Optional.empty()

            val exception = { billingService.cancelUserSubscription(user) }
            exception shouldThrow UserNotFoundException::class
        }

        @Test
        fun `should cancel subscription and deactivate user`() {
            val userId = UUID.randomUUID()
            val user = createUser(id = userId, waId = "5511999999999")
            val subscription = createSubscription(
                userId = userId,
                status = SubStatus.ACTIVE,
                asaasSubscriptionId = "asaas-sub-123"
            )
            val userEntity = createUserEntity(id = userId, isActive = true)

            every { subscriptionRepository.findActiveByUserId(userId) } returns subscription
            coEvery { asaasGateway.cancelSubscription("asaas-sub-123") } returns emptyMap()
            every { subscriptionRepository.save(any()) } answers { firstArg() }
            every { userRepository.findById(userId) } returns Optional.of(userEntity)
            every { userRepository.save(any()) } answers { firstArg() }

            val result = billingService.cancelUserSubscription(user)

            result.success shouldBeEqualTo true
            result.subscriptionId shouldBeEqualTo "asaas-sub-123"
            verify { subscriptionRepository.save(match { it.status == SubStatus.CANCELED }) }
            verify { userRepository.save(match { !it.isActive }) }
        }
    }

    @Nested
    inner class ExpireGracePeriodsTests {
        @Test
        fun `should return 0 when no expired subscriptions`() {
            every { subscriptionRepository.findExpiredGracePeriods(any()) } returns emptyList()

            val result = billingService.expireGracePeriods()

            result shouldBeEqualTo 0
        }

        @Test
        fun `should deactivate users with expired grace periods`() {
            val userId1 = UUID.randomUUID()
            val userId2 = UUID.randomUUID()
            val subscription1 = createSubscription(
                userId = userId1,
                status = SubStatus.PAST_DUE,
                graceExpiresAt = OffsetDateTime.now().minusHours(1)
            )
            val subscription2 = createSubscription(
                userId = userId2,
                status = SubStatus.PAST_DUE,
                graceExpiresAt = OffsetDateTime.now().minusHours(2)
            )
            val userEntity1 = createUserEntity(id = userId1, waId = "5511111111111", isActive = true)
            val userEntity2 = createUserEntity(id = userId2, waId = "5522222222222", isActive = true)

            every { subscriptionRepository.findExpiredGracePeriods(any()) } returns listOf(subscription1, subscription2)
            every { subscriptionRepository.save(any()) } answers { firstArg() }
            every { userRepository.findById(userId1) } returns Optional.of(userEntity1)
            every { userRepository.findById(userId2) } returns Optional.of(userEntity2)
            every { userRepository.save(any()) } answers { firstArg() }
            coEvery { chatwootGateway.sendMessageToPhone(any(), any()) } returns true

            val result = billingService.expireGracePeriods()

            result shouldBeEqualTo 2
            verify(exactly = 2) { subscriptionRepository.save(match { it.status == SubStatus.CANCELED }) }
            verify(exactly = 2) { userRepository.save(match { !it.isActive }) }
        }

        @Test
        fun `should not count already inactive users`() {
            val userId = UUID.randomUUID()
            val subscription = createSubscription(
                userId = userId,
                status = SubStatus.PAST_DUE,
                graceExpiresAt = OffsetDateTime.now().minusHours(1)
            )
            val userEntity = createUserEntity(id = userId, isActive = false)

            every { subscriptionRepository.findExpiredGracePeriods(any()) } returns listOf(subscription)
            every { subscriptionRepository.save(any()) } answers { firstArg() }
            every { userRepository.findById(userId) } returns Optional.of(userEntity)

            val result = billingService.expireGracePeriods()

            result shouldBeEqualTo 0
            verify(exactly = 0) { userRepository.save(any()) }
        }
    }

    @Nested
    inner class IdempotencyTests {
        @Test
        fun `should return true when event already processed`() {
            every { webhookEventRepository.existsByEventId("event-123") } returns true

            val result = billingService.isEventAlreadyProcessed("event-123")

            result shouldBeEqualTo true
        }

        @Test
        fun `should return false when event not processed`() {
            every { webhookEventRepository.existsByEventId("event-123") } returns false

            val result = billingService.isEventAlreadyProcessed("event-123")

            result shouldBeEqualTo false
        }

        @Test
        fun `should save webhook event when marking as processed`() {
            every { webhookEventRepository.save(any()) } answers { firstArg() }

            billingService.markEventProcessed("event-123", "PAYMENT_CONFIRMED")

            verify {
                webhookEventRepository.save(match {
                    it.eventId == "event-123" &&
                    it.eventType == "PAYMENT_CONFIRMED"
                })
            }
        }
    }

    // Helper functions
    private fun createSubscription(
        id: UUID? = UUID.randomUUID(),
        userId: UUID = UUID.randomUUID(),
        asaasSubscriptionId: String? = "sub-${UUID.randomUUID()}",
        status: SubStatus = SubStatus.ACTIVE,
        planName: String? = "STARTER",
        graceExpiresAt: OffsetDateTime? = null
    ) = Subscription(
        id = id,
        userId = userId,
        asaasSubscriptionId = asaasSubscriptionId,
        status = status,
        planName = planName,
        graceExpiresAt = graceExpiresAt
    )

    private fun createUser(
        id: UUID = UUID.randomUUID(),
        waId: String = "5511999999999",
        fullName: String? = "Test User",
        email: String? = "test@example.com",
        planType: String = "FREE",
        lifetime: Boolean = false,
        isActive: Boolean = true,
        createdAt: OffsetDateTime = OffsetDateTime.now(),
        updatedAt: OffsetDateTime = OffsetDateTime.now()
    ): User = User(
        id = id,
        waId = waId,
        fullName = fullName,
        email = email,
        planType = planType,
        lifetime = lifetime,
        isActive = isActive,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun createUserEntity(
        id: UUID? = UUID.randomUUID(),
        waId: String = "5511999999999",
        fullName: String? = "Test User",
        email: String? = "test@example.com",
        planType: String = "FREE",
        isActive: Boolean = true
    ): UserEntity = UserEntity(
        waId = waId,
        fullName = fullName,
        email = email,
        planType = planType,
        isActive = isActive
    ).apply { this.id = id }
}

