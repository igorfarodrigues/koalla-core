package ai.koalla.core.repository

import ai.koalla.core.entity.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.UUID

@Repository
interface AsaasCustomerRepository : JpaRepository<AsaasCustomer, UUID> {
    fun findByUserId(userId: UUID): AsaasCustomer?
    fun findByAsaasCustomerId(asaasCustomerId: String): AsaasCustomer?
}

@Repository
interface SubscriptionRepository : JpaRepository<Subscription, UUID> {
    fun findByUserId(userId: UUID): List<Subscription>
    fun findByAsaasSubscriptionId(asaasSubscriptionId: String): Subscription?

    @Query("""
        SELECT s FROM Subscription s 
        WHERE s.userId = :userId 
        AND s.status IN ('ACTIVE', 'TRIALING', 'PAST_DUE')
    """)
    fun findActiveByUserId(userId: UUID): Subscription?

    @Query("""
        SELECT s FROM Subscription s 
        WHERE s.status = 'PAST_DUE' 
        AND s.graceExpiresAt <= :now
    """)
    fun findExpiredGracePeriods(now: OffsetDateTime): List<Subscription>
}

@Repository
interface InvoiceRepository : JpaRepository<Invoice, UUID> {
    fun findByUserId(userId: UUID): List<Invoice>
    fun findByAsaasPaymentId(asaasPaymentId: String): Invoice?
    fun findBySubscriptionId(subscriptionId: UUID): List<Invoice>
}

@Repository
interface WebhookEventRepository : JpaRepository<WebhookEvent, UUID> {
    fun existsByEventId(eventId: String): Boolean
}

