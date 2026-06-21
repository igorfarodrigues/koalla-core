package ai.koalla.core.domain

/**
 * Movement direction of a financial transaction.
 * Lives in the domain package so domain models never import from the entity/infrastructure layer.
 */
enum class MovementType {
    CASH_IN,
    CASH_OUT,
}

/**
 * Tax/legal context of a transaction (Pessoa Física vs. Pessoa Jurídica).
 */
enum class EntityContext {
    PF,
    PJ,
}
