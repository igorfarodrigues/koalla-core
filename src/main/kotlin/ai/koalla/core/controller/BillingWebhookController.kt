package ai.koalla.core.controller

import ai.koalla.core.config.KoallaProperties
import ai.koalla.core.dto.AsaasWebhookPayload
import ai.koalla.core.dto.CancelSubscriptionResponse
import ai.koalla.core.repository.UserRepository
import ai.koalla.core.service.BillingService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * POST /webhook/asaas — receives billing events from Asaas.
 */
@RestController
@RequestMapping("/webhook")
@Tag(name = "Webhooks", description = "Endpoints para receber webhooks de integrações externas")
class BillingWebhookController(
    private val billingService: BillingService,
    private val userRepository: UserRepository,
    private val props: KoallaProperties
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        val PAYMENT_OK_EVENTS = setOf("PAYMENT_CONFIRMED", "PAYMENT_RECEIVED")
        val PAYMENT_FAIL_EVENTS = setOf("PAYMENT_OVERDUE", "PAYMENT_CREDIT_CARD_CAPTURE_REFUSED")
        val SUBSCRIPTION_END_EVENTS = setOf("SUBSCRIPTION_DELETED", "SUBSCRIPTION_INACTIVATED")
    }

    @PostMapping("/asaas")
    @Operation(
        summary = "Recebe webhooks do Asaas",
        description = """
            Endpoint que recebe eventos de cobrança do Asaas.
            
            **Eventos tratados:**
            - PAYMENT_CONFIRMED / PAYMENT_RECEIVED → ativa usuário
            - PAYMENT_OVERDUE / PAYMENT_CREDIT_CARD_CAPTURE_REFUSED → marca atraso
            - SUBSCRIPTION_DELETED / SUBSCRIPTION_INACTIVATED → cancela assinatura
            
            **Idempotência:**
            - Eventos duplicados são ignorados via webhook_events table
        """
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Webhook processado"),
        ApiResponse(responseCode = "401", description = "Token inválido")
    ])
    fun asaasWebhook(
        @RequestBody payload: AsaasWebhookPayload,
        @Parameter(description = "Token de autenticação Asaas")
        @RequestHeader("asaas-access-token", required = false) token: String?,
        request: HttpServletRequest
    ): ResponseEntity<Map<String, String>> {
        // Validate token
        if (props.asaas.webhookToken.isNotEmpty()) {
            if (token == null || !secureCompare(token, props.asaas.webhookToken)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(mapOf("error" to "Invalid webhook token"))
            }
        }

        val event = payload.event
        val eventId = payload.id

        // Idempotency check
        if (eventId.isNotEmpty() && billingService.isEventAlreadyProcessed(eventId)) {
            return ResponseEntity.ok(mapOf("status" to "duplicate", "event" to event))
        }

        val paymentId = payload.payment?.id ?: ""
        val subscriptionId = payload.payment?.subscription

        when {
            event in PAYMENT_OK_EVENTS -> {
                billingService.handlePaymentConfirmed(paymentId, subscriptionId)
            }
            event in PAYMENT_FAIL_EVENTS -> {
                billingService.handlePaymentOverdue(paymentId, subscriptionId)
            }
            event in SUBSCRIPTION_END_EVENTS -> {
                val subId = payload.subscription?.id ?: subscriptionId
                if (subId != null) {
                    billingService.handleSubscriptionDeleted(subId)
                }
            }
        }

        // Mark event as processed
        if (eventId.isNotEmpty()) {
            billingService.markEventProcessed(eventId, event)
        }

        return ResponseEntity.ok(mapOf("status" to "ok", "event" to event))
    }

    @PostMapping("/cancel-subscription/{waId}")
    @Operation(
        summary = "Cancelar assinatura",
        description = "Cancela a assinatura de um usuário no Asaas e desativa a conta"
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Assinatura cancelada",
            content = [Content(schema = Schema(implementation = CancelSubscriptionResponse::class))]),
        ApiResponse(responseCode = "404", description = "Usuário não encontrado"),
        ApiResponse(responseCode = "400", description = "Erro ao cancelar")
    ])
    fun cancelSubscription(
        @Parameter(description = "Número WhatsApp do usuário")
        @PathVariable waId: String
    ): ResponseEntity<Any> {
        val user = userRepository.findByWaId(waId)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(mapOf("error" to "User not found"))

        return try {
            val result = runBlocking {
                billingService.cancelUserSubscription(user)
            }
            ResponseEntity.ok(CancelSubscriptionResponse(
                success = result.success,
                subscriptionId = result.subscriptionId
            ))
        } catch (e: IllegalStateException) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(mapOf("error" to e.message))
        }
    }

    private fun secureCompare(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }
}
