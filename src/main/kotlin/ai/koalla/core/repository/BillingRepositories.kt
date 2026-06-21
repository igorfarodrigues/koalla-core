package ai.koalla.core.repository

import ai.koalla.core.entity.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.UUID

@Repository
interface AsaasCustomerRepository : JpaRepository<AsaasCustomer, UUID> {
    fun findByUserId(userId: UUID): AsaasCustomer?
}

@Repository
interface SubscriptionRepository : JpaRepository<Subscription, UUID> {
    fun findByAsaasSubscriptionId(asaasSubscriptionId: String): Subscription?

    @Query("""
        SELECT s FROM Subscription s
        WHERE s.userId = :userId
        AND s.status IN (:statuses)
    """)
    fun findActiveByUserId(
        @Param("userId") userId: UUID,
        @Param("statuses") statuses: Collection<SubStatus> = listOf(SubStatus.ACTIVE, SubStatus.TRIALING, SubStatus.PAST_DUE)
    ): Subscription?

    @Query("""
        SELECT s FROM Subscription s
        WHERE s.status = :status
        AND s.graceExpiresAt <= :now
    """)
    fun findExpiredGracePeriods(
        @Param("now") now: OffsetDateTime,
        @Param("status") status: SubStatus = SubStatus.PAST_DUE
    ): List<Subscription>
}

@Repository
interface InvoiceRepository : JpaRepository<Invoice, UUID> {
    fun findByAsaasPaymentId(asaasPaymentId: String): Invoice?
}

@Repository
interface WebhookEventRepository : JpaRepository<WebhookEvent, UUID> {
    fun existsByEventId(eventId: String): Boolean
}

