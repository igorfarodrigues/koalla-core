package ai.koalla.core.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

enum class SubStatus {
    TRIALING,
    ACTIVE,
    PAST_DUE,
    CANCELED,
    EXPIRED,
}

@Entity
@Table(name = "asaas_customers", schema = "koalla")
class AsaasCustomer(
    @Id
    @Column(name = "user_id")
    var userId: UUID,
    @Column(name = "asaas_customer_id", length = 50, unique = true, nullable = false)
    var asaasCustomerId: String,
    @Column(name = "created_at", updatable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),
)

@Entity
@Table(name = "subscriptions", schema = "koalla")
class Subscription(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,
    @Column(name = "user_id", nullable = false)
    var userId: UUID,
    @Column(name = "asaas_subscription_id", length = 50, unique = true)
    var asaasSubscriptionId: String? = null,
    @Enumerated(EnumType.STRING)
    var status: SubStatus = SubStatus.TRIALING,
    @Column(name = "plan_name", length = 50)
    var planName: String? = null,
    @Column(name = "next_due_date")
    var nextDueDate: LocalDate? = null,
    @Column(name = "grace_expires_at")
    var graceExpiresAt: OffsetDateTime? = null,
    @Column(name = "created_at", updatable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),
)

@Entity
@Table(name = "invoices", schema = "koalla")
class Invoice(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,
    @Column(name = "user_id", nullable = false)
    var userId: UUID,
    @Column(name = "subscription_id")
    var subscriptionId: UUID? = null,
    @Column(name = "asaas_payment_id", length = 50, unique = true)
    var asaasPaymentId: String? = null,
    @Column(precision = 12, scale = 2, nullable = false)
    var amount: BigDecimal,
    @Column(length = 30)
    var status: String? = null,
    @Column(name = "pix_code", columnDefinition = "TEXT")
    var pixCode: String? = null,
    @Column(name = "payment_link", columnDefinition = "TEXT")
    var paymentLink: String? = null,
    @Column(name = "created_at", updatable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),
)

@Entity
@Table(name = "webhook_events", schema = "koalla")
class WebhookEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,
    @Column(name = "event_id", length = 200, unique = true, nullable = false)
    var eventId: String,
    @Column(name = "event_type", length = 100, nullable = false)
    var eventType: String,
    @Column(name = "processed_at", updatable = false)
    var processedAt: OffsetDateTime = OffsetDateTime.now(),
)
