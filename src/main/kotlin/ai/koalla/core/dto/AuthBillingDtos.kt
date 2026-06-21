package ai.koalla.core.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

// ===================== Auth DTOs =====================

data class SignupRequest(
    @field:NotBlank
    @field:Pattern(regexp = "^\\d{10,15}$", message = "Invalid phone number")
    val waId: String,

    @field:NotBlank
    val fullName: String,

    @field:Email
    val email: String,

    @field:NotBlank
    @field:Pattern(regexp = "^(STARTER|PRO|BUSINESS)$", message = "Invalid plan")
    val plan: String,

    val card: CardData,
    val cardHolderInfo: CardHolderInfo
)

data class CardData(
    @field:NotBlank
    val holderName: String,
    @field:NotBlank
    val number: String,
    @field:NotBlank
    val expiryMonth: String,
    @field:NotBlank
    val expiryYear: String,
    @field:NotBlank
    val ccv: String
)

data class CardHolderInfo(
    @field:NotBlank
    val name: String,
    @field:Email
    val email: String,
    @field:NotBlank
    val cpfCnpj: String,
    val postalCode: String? = null,
    val addressNumber: String? = null,
    val phone: String? = null
)

data class SignupResponse(
    val subscriptionId: String,
    val trialEndDate: String,
    val plan: String,
    val waLink: String
)

data class MagicLinkRequest(
    @field:Email
    val email: String
)

data class MagicLinkVerifyRequest(
    @field:NotBlank
    val token: String
)

data class AuthTokenResponse(
    val accessToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long
)

// ===================== Billing DTOs =====================

data class CancelSubscriptionResponse(
    val success: Boolean,
    val subscriptionId: String?
)

// ===================== Asaas Webhook DTOs =====================

@JsonIgnoreProperties(ignoreUnknown = true)
data class AsaasWebhookPayload(
    val id: String = "",           // Asaas event ID (evt_xxx) — used for idempotency
    val event: String,
    val payment: AsaasPayment? = null,
    val subscription: AsaasSubscription? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AsaasPayment(
    val id: String,
    val customer: String? = null,
    val subscription: String? = null,
    val value: Double? = null,
    val status: String? = null,
    @JsonProperty("invoiceUrl")
    val invoiceUrl: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AsaasSubscription(
    val id: String,
    val customer: String? = null,
    val status: String? = null
)

