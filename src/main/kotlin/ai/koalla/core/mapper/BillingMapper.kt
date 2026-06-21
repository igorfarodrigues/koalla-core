package ai.koalla.core.mapper

import ai.koalla.core.domain.AsaasCustomerMapping
import ai.koalla.core.domain.Invoice
import ai.koalla.core.domain.Subscription
import ai.koalla.core.domain.SubscriptionStatus
import ai.koalla.core.entity.AsaasCustomer
import ai.koalla.core.entity.SubStatus

// ── Entity → Domain ──────────────────────────────────────────────────────────

fun ai.koalla.core.entity.Subscription.toDomain(): Subscription =
    Subscription(
        id = id!!,
        userId = userId,
        asaasSubscriptionId = asaasSubscriptionId,
        status = status.toDomain(),
        planName = planName,
        nextDueDate = nextDueDate,
        graceExpiresAt = graceExpiresAt,
        createdAt = createdAt,
    )

fun ai.koalla.core.entity.Invoice.toDomain(): Invoice =
    Invoice(
        id = id!!,
        userId = userId,
        subscriptionId = subscriptionId,
        asaasPaymentId = asaasPaymentId,
        amount = amount,
        status = status,
        pixCode = pixCode,
        paymentLink = paymentLink,
        createdAt = createdAt,
    )

fun AsaasCustomer.toDomain(): AsaasCustomerMapping =
    AsaasCustomerMapping(
        userId = userId,
        asaasCustomerId = asaasCustomerId,
        createdAt = createdAt,
    )

// ── Entity Enum → Domain Enum ────────────────────────────────────────────────

fun SubStatus.toDomain(): SubscriptionStatus =
    when (this) {
        SubStatus.TRIALING -> SubscriptionStatus.TRIALING
        SubStatus.ACTIVE -> SubscriptionStatus.ACTIVE
        SubStatus.PAST_DUE -> SubscriptionStatus.PAST_DUE
        SubStatus.CANCELED -> SubscriptionStatus.CANCELED
        SubStatus.EXPIRED -> SubscriptionStatus.EXPIRED
    }

fun SubscriptionStatus.toEntity(): SubStatus =
    when (this) {
        SubscriptionStatus.TRIALING -> SubStatus.TRIALING
        SubscriptionStatus.ACTIVE -> SubStatus.ACTIVE
        SubscriptionStatus.PAST_DUE -> SubStatus.PAST_DUE
        SubscriptionStatus.CANCELED -> SubStatus.CANCELED
        SubscriptionStatus.EXPIRED -> SubStatus.EXPIRED
    }
