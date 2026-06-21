package ai.koalla.core.controller

import ai.koalla.core.config.KoallaProperties
import ai.koalla.core.dto.ChatwootWebhookBody
import ai.koalla.core.dto.WebhookResponse
import ai.koalla.core.service.MessagePipelineService
import ai.koalla.core.util.secureCompare
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * POST /webhook/chatwoot — receives Chatwoot webhook events.
 */
@RestController
@RequestMapping("/webhook")
@Tag(name = "Webhooks", description = "Endpoints para receber webhooks de integrações externas")
class WebhookController(
    private val messagePipelineService: MessagePipelineService,
    private val props: KoallaProperties,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @PostMapping("/chatwoot")
    @Operation(
        summary = "Recebe webhooks do Chatwoot",
        description = """
            Endpoint que recebe eventos do Chatwoot (mensagens WhatsApp).
            
            **Fluxo:**
            1. Valida header X-Koalla-Secret
            2. Processa apenas eventos 'message_created'
            3. Executa pipeline: debounce → lock → agent → resposta
            
            **Segurança:**
            - Header X-Koalla-Secret deve corresponder ao valor configurado
            - Retorna 401 se inválido
        """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Webhook processado com sucesso",
                content = [Content(schema = Schema(implementation = WebhookResponse::class))],
            ),
            ApiResponse(responseCode = "401", description = "Secret inválido ou ausente"),
        ],
    )
    fun chatwootWebhook(
        @RequestBody body: ChatwootWebhookBody,
        @Parameter(description = "Secret de autenticação do webhook")
        @RequestHeader("X-Koalla-Secret", required = false) secret: String?,
        request: HttpServletRequest,
    ): ResponseEntity<WebhookResponse> {
        // =====================================================================
        // WEBHOOK SECURITY
        // =====================================================================
        // Chatwoot should send:
        // X-Koalla-Secret: value_from_env
        //
        // If the value is different:
        // HTTP 401 Unauthorized
        // =====================================================================

        if (!secureCompare(secret ?: "", props.chatwoot.webhookSecret)) {
            logger.warn("Unauthorized webhook attempt from ${request.remoteAddr}")
            return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(WebhookResponse(status = "unauthorized"))
        }

        // =====================================================================
        // PROCESS ONLY message_created EVENTS
        // =====================================================================

        if (body.event != "message_created") {
            return ResponseEntity.ok(
                WebhookResponse(
                    status = "ignored",
                    event = body.event,
                ),
            )
        }

        // =====================================================================
        // BACKGROUND PROCESSING
        // =====================================================================

        // Launch in background — returns immediately
        messagePipelineService.processWebhook(body)

        return ResponseEntity.ok(WebhookResponse(status = "queued"))
    }
}
