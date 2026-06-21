package ai.koalla.core.domain

import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Pure domain model for a billing subscription.
 * No JPA annotations — safe to use across all layers.
 */
data class Subscription(
    val id: UUID,
    val userId: UUID,
    val asaasSubscriptionId: String?,
    val status: SubscriptionStatus,
    val planName: String?,
    val nextDueDate: LocalDate?,
    val graceExpiresAt: OffsetDateTime?,
    val createdAt: OffsetDateTime,
)

/**
 * Pure domain model for a billing invoice.
 * No JPA annotations — safe to use across all layers.
 */
data class Invoice(
    val id: UUID,
    val userId: UUID,
    val subscriptionId: UUID?,
    val asaasPaymentId: String?,
    val amount: BigDecimal,
    val status: String?,
    val pixCode: String?,
    val paymentLink: String?,
    val createdAt: OffsetDateTime,
)

/**
 * Pure domain model for an Asaas customer mapping.
 * No JPA annotations — safe to use across all layers.
 */
data class AsaasCustomerMapping(
    val userId: UUID,
    val asaasCustomerId: String,
    val createdAt: OffsetDateTime,
)
