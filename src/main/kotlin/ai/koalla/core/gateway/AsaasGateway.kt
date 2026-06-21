package ai.koalla.core.gateway

import ai.koalla.core.dto.CardData
import ai.koalla.core.dto.CardHolderInfo
import java.math.BigDecimal

/**
 * Port interface for Asaas billing API operations.
 * Decouples BillingService from the concrete WebClient implementation.
 */
interface AsaasGateway {
    suspend fun getOrCreateCustomer(
        name: String,
        phone: String,
        email: String? = null,
        cpf: String? = null,
    ): Map<String, Any>

    suspend fun createSubscription(
        customerId: String,
        plan: String,
        cardData: CardData,
        cardHolderInfo: CardHolderInfo,
        trialDays: Int? = null,
    ): Map<String, Any>

    suspend fun getSubscription(subscriptionId: String): Map<String, Any>

    suspend fun cancelSubscription(subscriptionId: String): Map<String, Any>

    suspend fun listSubscriptionPayments(subscriptionId: String): List<Map<String, Any>>

    suspend fun getOrCreateCharge(
        customerId: String,
        value: BigDecimal,
        dueDate: String,
        existingChargeId: String? = null,
    ): Map<String, Any>

    suspend fun getPixQrCode(paymentId: String): Map<String, Any>
}
