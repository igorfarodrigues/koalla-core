package ai.koalla.core.client

import ai.koalla.core.config.KoallaProperties
import ai.koalla.core.dto.CardData
import ai.koalla.core.dto.CardHolderInfo
import ai.koalla.core.gateway.AsaasGateway
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Asaas billing HTTP adapter — implements [AsaasGateway].
 * Handles: customers, subscriptions (recurring with trial) and payments.
 */
@Component
class AsaasClient(
    private val asaasWebClient: WebClient,
    private val props: KoallaProperties
) : AsaasGateway {

    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        val PLAN_LABELS = mapOf(
            "STARTER" to "Koalla Starter",
            "PRO" to "Koalla Pro",
            "BUSINESS" to "Koalla Business"
        )
    }

    override suspend fun getOrCreateCustomer(
        name: String,
        phone: String,
        email: String?,
        cpf: String?
    ): Map<String, Any> {
        val searchResult = asaasWebClient.get()
            .uri("/v3/customers?mobilePhone=$phone")
            .retrieve()
            .awaitBody<Map<String, Any>>()

        @Suppress("UNCHECKED_CAST")
        val existingCustomers = searchResult["data"] as? List<Map<String, Any>>
        if (!existingCustomers.isNullOrEmpty()) return existingCustomers[0]

        val payload = mutableMapOf<String, Any>("name" to name, "mobilePhone" to phone)
        email?.let { payload["email"] = it }
        cpf?.let { payload["cpfCnpj"] = it }

        return asaasWebClient.post()
            .uri("/v3/customers")
            .bodyValue(payload)
            .retrieve()
            .awaitBody<Map<String, Any>>()
    }

    override suspend fun createSubscription(
        customerId: String,
        plan: String,
        cardData: CardData,
        cardHolderInfo: CardHolderInfo,
        trialDays: Int?
    ): Map<String, Any> {
        val effectiveTrial = trialDays ?: props.trialDays
        val nextDue = LocalDate.now().plusDays(effectiveTrial.toLong())
        val planUpper = plan.uppercase()
        val planValue = props.plans.getValue(planUpper)

        val payload = mutableMapOf<String, Any>(
            "customer" to customerId,
            "billingType" to "CREDIT_CARD",
            "value" to planValue,
            "nextDueDate" to nextDue.toString(),
            "cycle" to "MONTHLY",
            "description" to (PLAN_LABELS[planUpper] ?: plan),
            "creditCard" to mapOf(
                "holderName" to cardData.holderName,
                "number" to cardData.number,
                "expiryMonth" to cardData.expiryMonth,
                "expiryYear" to cardData.expiryYear,
                "ccv" to cardData.ccv
            ),
            "creditCardHolderInfo" to buildMap {
                put("name", cardHolderInfo.name)
                put("email", cardHolderInfo.email)
                put("cpfCnpj", cardHolderInfo.cpfCnpj)
                cardHolderInfo.phone?.let { put("phone", it) }
                cardHolderInfo.postalCode?.let { put("postalCode", it) }
                cardHolderInfo.addressNumber?.let { put("addressNumber", it) }
            }
        )

        if (effectiveTrial > 0) {
            payload["trialEndDate"] = LocalDate.now().plusDays(effectiveTrial.toLong()).toString()
        }

        return asaasWebClient.post()
            .uri("/v3/subscriptions")
            .bodyValue(payload)
            .retrieve()
            .awaitBody<Map<String, Any>>()
    }

    override suspend fun getSubscription(subscriptionId: String): Map<String, Any> =
        asaasWebClient.get()
            .uri("/v3/subscriptions/$subscriptionId")
            .retrieve()
            .awaitBody<Map<String, Any>>()

    override suspend fun cancelSubscription(subscriptionId: String): Map<String, Any> =
        asaasWebClient.delete()
            .uri("/v3/subscriptions/$subscriptionId")
            .retrieve()
            .awaitBody<Map<String, Any>>()

    override suspend fun listSubscriptionPayments(subscriptionId: String): List<Map<String, Any>> {
        val response = asaasWebClient.get()
            .uri("/v3/payments?subscription=$subscriptionId")
            .retrieve()
            .awaitBody<Map<String, Any>>()

        @Suppress("UNCHECKED_CAST")
        return response["data"] as? List<Map<String, Any>> ?: emptyList()
    }

    override suspend fun getOrCreateCharge(
        customerId: String,
        value: BigDecimal,
        dueDate: String,
        existingChargeId: String?
    ): Map<String, Any> {
        if (existingChargeId != null) {
            try {
                return asaasWebClient.get()
                    .uri("/v3/payments/$existingChargeId")
                    .retrieve()
                    .awaitBody<Map<String, Any>>()
            } catch (e: Exception) {
                logger.debug("Existing charge not found: $existingChargeId")
            }
        }

        val payload = mapOf(
            "customer" to customerId,
            "billingType" to "PIX",
            "value" to value,
            "dueDate" to dueDate
        )
        return asaasWebClient.post()
            .uri("/v3/payments")
            .bodyValue(payload)
            .retrieve()
            .awaitBody<Map<String, Any>>()
    }

    override suspend fun getPixQrCode(paymentId: String): Map<String, Any> =
        asaasWebClient.get()
            .uri("/v3/payments/$paymentId/pixQrCode")
            .retrieve()
            .awaitBody<Map<String, Any>>()
}
