package ai.koalla.core.domain

/**
 * Status of a subscription lifecycle.
 * Lives in the domain package so domain models never import from the entity/infrastructure layer.
 */
enum class SubscriptionStatus {
    TRIALING,
    ACTIVE,
    PAST_DUE,
    CANCELED,
    EXPIRED
}

