package ai.koalla.core.exception

/**
 * Root of the Koalla exception hierarchy.
 * All domain-level exceptions extend this sealed class so callers can catch
 * KoallaException for a global fallback without swallowing unrelated errors.
 */
sealed class KoallaException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** Thrown when a user cannot be found by ID, waId, or email. */
class UserNotFoundException(identifier: String) : KoallaException("User not found: $identifier")

/** Thrown when an operation requires an active user but the account is inactive. */
class UserInactiveException(waId: String) : KoallaException("User inactive: $waId")

/** Thrown when an operation requires an active subscription but none exists. */
class SubscriptionNotFoundException(userId: String) : KoallaException("No active subscription for user: $userId")

/** Thrown for billing/payment failures in Asaas integration. */
class BillingException(message: String, cause: Throwable? = null) : KoallaException(message, cause)

/** Thrown when an unknown or disallowed plan name is supplied. */
class InvalidPlanException(plan: String) : KoallaException("Invalid plan: $plan. Valid values: STARTER, PRO, BUSINESS")

/** Generic not-found exception for resources without a more specific type. */
class ResourceNotFoundException(message: String) : KoallaException(message)
