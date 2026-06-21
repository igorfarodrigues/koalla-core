package ai.koalla.core.service

import ai.koalla.core.domain.User
import ai.koalla.core.entity.AsaasCustomer
import ai.koalla.core.entity.Invoice
import ai.koalla.core.entity.SubStatus
import ai.koalla.core.entity.Subscription
import ai.koalla.core.exception.SubscriptionNotFoundException
import ai.koalla.core.exception.UserNotFoundException
import ai.koalla.core.gateway.AsaasGateway
import ai.koalla.core.gateway.ChatwootGateway
import ai.koalla.core.config.KoallaProperties
import ai.koalla.core.dto.CardData
import ai.koalla.core.dto.CardHolderInfo
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
 *   4. Activates user (isActive=true, planType=plan, status=TRIALING)
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
    private val asaasGateway: AsaasGateway,
    private val chatwootGateway: ChatwootGateway,
    private val props: KoallaProperties
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    // ── Signup ────────────────────────────────────────────────────────────────

    /**
     * NOTE: This method is intentionally NOT suspend.
     * Spring's @Transactional uses ThreadLocal, which is unsafe with coroutine suspension
     * (the coroutine may resume on a different thread, losing the transaction context).
     * Gateway calls (Asaas) are wrapped in runBlocking to bridge suspend → blocking.
     */
    @Transactional
    fun startTrial(
        user: User,
        plan: String,
        cardData: CardData,
        cardHolderInfo: CardHolderInfo
    ): TrialResult {
        val planUpper = plan.uppercase()

        // 1. Asaas customer
        val existingCustomer = asaasCustomerRepository.findByUserId(user.id)
        val asaasCustomerId: String

        if (existingCustomer != null) {
            asaasCustomerId = existingCustomer.asaasCustomerId
        } else {
            val customer = runBlocking {
                asaasGateway.getOrCreateCustomer(
                    name = user.fullName ?: "",
                    phone = user.waId,
                    email = user.email
                )
            }
            asaasCustomerId = customer["id"] as String
            asaasCustomerRepository.save(
                AsaasCustomer(userId = user.id, asaasCustomerId = asaasCustomerId)
            )
        }

        // 2. Asaas subscription
        val subscriptionData = runBlocking {
            asaasGateway.createSubscription(
                customerId = asaasCustomerId,
                plan = planUpper,
                cardData = cardData,
                cardHolderInfo = cardHolderInfo,
                trialDays = props.trialDays
            )
        }

        val asaasSubscriptionId = subscriptionData["id"] as String
        val trialEnd = LocalDate.now().plusDays(props.trialDays.toLong())

        // 3. Persist subscription
        subscriptionRepository.save(
            Subscription(
                userId = user.id,
                asaasSubscriptionId = asaasSubscriptionId,
                status = SubStatus.TRIALING,
                planName = planUpper,
                nextDueDate = trialEnd
            )
        )

        // 4. Activate user via entity
        val userEntity = userRepository.findById(user.id)
            .orElseThrow { UserNotFoundException(user.id.toString()) }
        userEntity.isActive = true
        userEntity.planType = planUpper
        userRepository.save(userEntity)

        return TrialResult(
            subscriptionId = asaasSubscriptionId,
            trialEndDate = trialEnd.toString(),
            plan = planUpper
        )
    }

    // ── Payment confirmed ─────────────────────────────────────────────────────

    @Transactional
    fun handlePaymentConfirmed(paymentId: String, subscriptionId: String?) {
        if (subscriptionId == null) return

        val subscription = subscriptionRepository.findByAsaasSubscriptionId(subscriptionId) ?: return

        var invoice = invoiceRepository.findByAsaasPaymentId(paymentId)
        if (invoice == null) {
            invoice = Invoice(
                userId = subscription.userId,
                subscriptionId = subscription.id,
                asaasPaymentId = paymentId,
                amount = props.plans.getValue(subscription.planName ?: "STARTER"),
                status = "CONFIRMED"
            )
        } else {
            invoice.status = "CONFIRMED"
        }
        invoiceRepository.save(invoice)

        subscription.status = SubStatus.ACTIVE
        subscription.graceExpiresAt = null
        subscriptionRepository.save(subscription)

        userRepository.findById(subscription.userId).ifPresent { userEntity ->
            if (!userEntity.isActive) {
                userEntity.isActive = true
                userRepository.save(userEntity)
            }
        }
    }

    // ── Payment overdue ───────────────────────────────────────────────────────

    @Transactional
    fun handlePaymentOverdue(paymentId: String, subscriptionId: String?) {
        if (subscriptionId == null) return

        val subscription = subscriptionRepository.findByAsaasSubscriptionId(subscriptionId) ?: return

        if (subscription.status != SubStatus.PAST_DUE) {
            subscription.status = SubStatus.PAST_DUE
            subscription.graceExpiresAt = OffsetDateTime.now().plusHours(props.graceHours.toLong())
            subscriptionRepository.save(subscription)

            userRepository.findById(subscription.userId).ifPresent { userEntity ->
                runBlocking { notifyPaymentOverdue(userEntity.waId) }
            }
        }

        var invoice = invoiceRepository.findByAsaasPaymentId(paymentId)
        if (invoice != null) {
            invoice.status = "OVERDUE"
        } else {
            invoice = Invoice(
                userId = subscription.userId,
                subscriptionId = subscription.id,
                asaasPaymentId = paymentId,
                amount = props.plans.getValue(subscription.planName ?: "STARTER"),
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
            chatwootGateway.sendMessageToPhone(waId, message)
        } catch (e: Exception) {
            logger.warn("Failed to notify user $waId about overdue payment: ${e.message}")
        }
    }

    // ── Grace period expiry job ────────────────────────────────────────────────

    @Scheduled(fixedRate = 3_600_000) // every hour
    @Transactional
    fun expireGracePeriods(): Int {
        val now = OffsetDateTime.now()
        val expiredSubs = subscriptionRepository.findExpiredGracePeriods(now)

        var deactivated = 0
        for (sub in expiredSubs) {
            sub.status = SubStatus.CANCELED
            subscriptionRepository.save(sub)

            userRepository.findById(sub.userId).ifPresent { userEntity ->
                if (userEntity.isActive) {
                    userEntity.isActive = false
                    userRepository.save(userEntity)
                    deactivated++
                    logger.info("User ${userEntity.waId} deactivated due to expired grace period")
                    runBlocking { notifyAccessRevoked(userEntity.waId) }
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
            chatwootGateway.sendMessageToPhone(waId, message)
        } catch (e: Exception) {
            logger.warn("Failed to notify user $waId about suspension: ${e.message}")
        }
    }

    // ── Subscription deleted ──────────────────────────────────────────────────

    @Transactional
    fun handleSubscriptionDeleted(subscriptionId: String) {
        val subscription = subscriptionRepository.findByAsaasSubscriptionId(subscriptionId) ?: return

        subscription.status = SubStatus.CANCELED
        subscriptionRepository.save(subscription)

        userRepository.findById(subscription.userId).ifPresent { userEntity ->
            if (userEntity.isActive) {
                userEntity.isActive = false
                userRepository.save(userEntity)
                logger.info("User ${userEntity.waId} deactivated due to subscription deletion/inactivation")
                runBlocking { notifyAccessRevoked(userEntity.waId) }
            }
        }
    }

    // ── Cancel user subscription ──────────────────────────────────────────────

    /**
     * NOTE: Not suspend — same reasoning as startTrial.
     */
    @Transactional
    fun cancelUserSubscription(user: User): CancelResult {
        val subscription = subscriptionRepository.findActiveByUserId(user.id)
            ?: throw SubscriptionNotFoundException(user.id.toString())

        runBlocking { asaasGateway.cancelSubscription(subscription.asaasSubscriptionId!!) }

        subscription.status = SubStatus.CANCELED
        subscriptionRepository.save(subscription)

        val userEntity = userRepository.findById(user.id)
            .orElseThrow { UserNotFoundException(user.id.toString()) }
        userEntity.isActive = false
        userRepository.save(userEntity)

        logger.info("Subscription canceled. user=${user.waId} subscription=${subscription.asaasSubscriptionId}")

        return CancelResult(success = true, subscriptionId = subscription.asaasSubscriptionId)
    }

    // ── Idempotency ───────────────────────────────────────────────────────────

    fun isEventAlreadyProcessed(eventId: String): Boolean =
        webhookEventRepository.existsByEventId(eventId)

    @Transactional
    fun markEventProcessed(eventId: String, eventType: String) {
        webhookEventRepository.save(
            ai.koalla.core.entity.WebhookEvent(eventId = eventId, eventType = eventType)
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
