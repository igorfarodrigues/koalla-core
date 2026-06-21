package ai.koalla.core.service

import ai.koalla.core.client.AsaasClient
import ai.koalla.core.client.ChatwootClient
import ai.koalla.core.config.KoallaProperties
import ai.koalla.core.dto.CardData
import ai.koalla.core.dto.CardHolderInfo
import ai.koalla.core.entity.*
import ai.koalla.core.repository.*
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Billing service — orchestrates signup with trial:
 *   1. Creates/retrieves customer in Asaas
 *   2. Creates recurring subscription with trialEndDate
 *   3. Persists AsaasCustomer + Subscription + Invoice in database
 *   4. Activates user (is_active=True, plan_type=plan, status=TRIALING)
 *
 * Grace period:
 *   When a payment is late, the user gets 48h before being deactivated.
 *   The job `expireGracePeriods` runs every hour checking expired subscriptions.
 */
@Service
class BillingService(
    private val userRepository: UserRepository,
    private val asaasCustomerRepository: AsaasCustomerRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val invoiceRepository: InvoiceRepository,
    private val webhookEventRepository: WebhookEventRepository,
    private val asaasClient: AsaasClient,
    private val chatwootClient: ChatwootClient,
    private val props: KoallaProperties
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Entry point called by /auth/signup.
     */
    @Transactional
    suspend fun startTrial(
        user: User,
        plan: String,
        cardData: CardData,
        cardHolderInfo: CardHolderInfo
    ): TrialResult {
        val planUpper = plan.uppercase()

        // 1. Asaas customer
        val existingCustomer = asaasCustomerRepository.findByUserId(user.id!!)
        val asaasCustomerId: String

        if (existingCustomer != null) {
            asaasCustomerId = existingCustomer.asaasCustomerId
        } else {
            val customer = asaasClient.getOrCreateCustomer(
                name = user.fullName ?: "",
                phone = user.waId,
                email = user.email
            )
            asaasCustomerId = customer["id"] as String
            asaasCustomerRepository.save(
                AsaasCustomer(
                    userId = user.id!!,
                    asaasCustomerId = asaasCustomerId
                )
            )
        }

        // 2. Asaas subscription
        val subscriptionData = asaasClient.createSubscription(
            customerId = asaasCustomerId,
            plan = planUpper,
            cardData = cardData,
            cardHolderInfo = cardHolderInfo,
            trialDays = props.trialDays
        )

        val asaasSubscriptionId = subscriptionData["id"] as String
        val trialEnd = LocalDate.now().plusDays(props.trialDays.toLong())

        // 3. Persist subscription
        val subscription = Subscription(
            userId = user.id!!,
            asaasSubscriptionId = asaasSubscriptionId,
            status = SubStatus.TRIALING,
            planName = planUpper,
            nextDueDate = trialEnd
        )
        subscriptionRepository.save(subscription)

        // 4. Activate user
        user.isActive = true
        user.planType = planUpper
        userRepository.save(user)

        return TrialResult(
            subscriptionId = asaasSubscriptionId,
            trialEndDate = trialEnd.toString(),
            plan = planUpper
        )
    }

    /**
     * Webhook PAYMENT_CONFIRMED / PAYMENT_RECEIVED:
     * - Records invoice as paid
     * - Ensures user is active
     */
    @Transactional
    fun handlePaymentConfirmed(paymentId: String, subscriptionId: String?) {
        if (subscriptionId == null) return

        val subscription = subscriptionRepository.findByAsaasSubscriptionId(subscriptionId) ?: return

        // Upsert invoice
        var invoice = invoiceRepository.findByAsaasPaymentId(paymentId)
        if (invoice == null) {
            invoice = Invoice(
                userId = subscription.userId,
                subscriptionId = subscription.id,
                asaasPaymentId = paymentId,
                amount = props.plans.getValue(subscription.planName ?: ""),
                status = "CONFIRMED"
            )
        } else {
            invoice.status = "CONFIRMED"
        }
        invoiceRepository.save(invoice)

        // Ensure user is active and subscription ACTIVE
        subscription.status = SubStatus.ACTIVE
        subscription.graceExpiresAt = null
        subscriptionRepository.save(subscription)

        val user = userRepository.findById(subscription.userId).orElse(null)
        if (user != null && !user.isActive) {
            user.isActive = true
            userRepository.save(user)
        }
    }

    /**
     * Webhook PAYMENT_OVERDUE / PAYMENT_CREDIT_CARD_CAPTURE_REFUSED:
     * - Upsert invoice as OVERDUE
     * - Marks subscription as PAST_DUE with grace_expires_at = now + 48h
     * - Notifies user via WhatsApp
     * - Does NOT deactivate immediately — the job expireGracePeriods does that
     */
    @Transactional
    fun handlePaymentOverdue(paymentId: String, subscriptionId: String?) {
        if (subscriptionId == null) return

        val subscription = subscriptionRepository.findByAsaasSubscriptionId(subscriptionId) ?: return

        // Set grace period only if not already PAST_DUE (avoid resetting timer)
        if (subscription.status != SubStatus.PAST_DUE) {
            subscription.status = SubStatus.PAST_DUE
            subscription.graceExpiresAt = OffsetDateTime.now().plusHours(props.graceHours.toLong())
            subscriptionRepository.save(subscription)

            // Notify user via WhatsApp
            val user = userRepository.findById(subscription.userId).orElse(null)
            if (user != null) {
                runBlocking {
                    notifyPaymentOverdue(user.waId)
                }
            }
        }

        // Upsert invoice
        var invoice = invoiceRepository.findByAsaasPaymentId(paymentId)
        if (invoice != null) {
            invoice.status = "OVERDUE"
        } else {
            invoice = Invoice(
                userId = subscription.userId,
                subscriptionId = subscription.id,
                asaasPaymentId = paymentId,
                amount = props.plans.getValue(subscription.planName ?: ""),
                status = "OVERDUE"
            )
        }
        invoiceRepository.save(invoice)
    }

    private suspend fun notifyPaymentOverdue(waId: String) {
        val message = """
            ⚠️ *Koalla — Pagamento pendente*
            
            Identificamos um problema com a cobrança da sua assinatura.
            
            Você tem *${props.graceHours} horas* para regularizar antes que o acesso seja suspenso.
            
            Se precisar de ajuda, é só responder aqui. 🐨
        """.trimIndent()

        try {
            chatwootClient.sendMessageToPhone(waId, message)
        } catch (e: Exception) {
            logger.warn("Failed to notify user $waId about overdue payment: ${e.message}")
        }
    }

    /**
     * Periodic job — deactivates users whose grace period has expired.
     * Called by Spring Scheduler every hour.
     */
    @Scheduled(fixedRate = 3600000) // 1 hour in milliseconds
    @Transactional
    fun expireGracePeriods(): Int {
        val now = OffsetDateTime.now()
        val expiredSubs = subscriptionRepository.findExpiredGracePeriods(now)

        var deactivated = 0
        for (sub in expiredSubs) {
            sub.status = SubStatus.CANCELED
            subscriptionRepository.save(sub)

            val user = userRepository.findById(sub.userId).orElse(null)
            if (user != null && user.isActive) {
                user.isActive = false
                userRepository.save(user)
                deactivated++
                logger.info("User ${user.waId} deactivated due to expired grace period")

                runBlocking {
                    notifyAccessRevoked(user.waId)
                }
            }
        }

        if (deactivated > 0) {
            logger.info("Grace periods expired: $deactivated user(s) deactivated")
        }

        return deactivated
    }

    private suspend fun notifyAccessRevoked(waId: String) {
        val message = """
            🔒 *Koalla — Acesso suspenso*
            
            Infelizmente não conseguimos processar o pagamento da sua assinatura e seu acesso foi suspenso.
            
            Para reativar, entre em contato com o suporte. 🐨
        """.trimIndent()

        try {
            chatwootClient.sendMessageToPhone(waId, message)
        } catch (e: Exception) {
            logger.warn("Failed to notify user $waId about suspension: ${e.message}")
        }
    }

    /**
     * Webhook SUBSCRIPTION_DELETED / SUBSCRIPTION_INACTIVATED:
     * - Marks subscription as CANCELED
     * - Deactivates user
     */
    @Transactional
    fun handleSubscriptionDeleted(subscriptionId: String) {
        val subscription = subscriptionRepository.findByAsaasSubscriptionId(subscriptionId) ?: return

        subscription.status = SubStatus.CANCELED
        subscriptionRepository.save(subscription)

        val user = userRepository.findById(subscription.userId).orElse(null)
        if (user != null) {
            user.isActive = false
            userRepository.save(user)
        }
    }

    // ── Idempotency ───────────────────────────────────────────────────────────

    fun isEventAlreadyProcessed(eventId: String): Boolean {
        return webhookEventRepository.existsByEventId(eventId)
    }

    @Transactional
    fun markEventProcessed(eventId: String, eventType: String) {
        webhookEventRepository.save(
            WebhookEvent(
                eventId = eventId,
                eventType = eventType
            )
        )
    }

    /**
     * Cancel user's active subscription.
     */
    @Transactional
    suspend fun cancelUserSubscription(user: User): CancelResult {
        val subscription = subscriptionRepository.findActiveByUserId(user.id!!)
            ?: throw IllegalStateException("User has no active subscription")

        asaasClient.cancelSubscription(subscription.asaasSubscriptionId!!)

        subscription.status = SubStatus.CANCELED
        subscriptionRepository.save(subscription)

        user.isActive = false
        userRepository.save(user)

        logger.info("Subscription canceled. user=${user.waId} subscription=${subscription.asaasSubscriptionId}")

        return CancelResult(
            success = true,
            subscriptionId = subscription.asaasSubscriptionId
        )
    }
}

data class TrialResult(
    val subscriptionId: String,
    val trialEndDate: String,
    val plan: String
)

data class CancelResult(
    val success: Boolean,
    val subscriptionId: String?
)

