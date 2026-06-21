package ai.koalla.core.controller

import ai.koalla.core.config.KoallaProperties
import ai.koalla.core.dto.ChatwootWebhookBody
import ai.koalla.core.dto.WebhookResponse
import ai.koalla.core.service.MessagePipelineService
import jakarta.servlet.http.HttpServletRequest
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.security.MessageDigest

/**
 * POST /webhook/chatwoot — receives Chatwoot webhook events.
 */
@RestController
@RequestMapping("/webhook")
class WebhookController(
    private val messagePipelineService: MessagePipelineService,
    private val props: KoallaProperties
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @PostMapping("/chatwoot")
    fun chatwootWebhook(
        @RequestBody body: ChatwootWebhookBody,
        @RequestHeader("X-Koalla-Secret", required = false) secret: String?,
        request: HttpServletRequest
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
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(WebhookResponse(status = "unauthorized"))
        }

        // =====================================================================
        // PROCESS ONLY message_created EVENTS
        // =====================================================================

        if (body.event != "message_created") {
            return ResponseEntity.ok(WebhookResponse(
                status = "ignored",
                event = body.event
            ))
        }

        // =====================================================================
        // BACKGROUND PROCESSING
        // =====================================================================

        // Process in background using coroutines
        runBlocking {
            messagePipelineService.processWebhook(body)
        }

        return ResponseEntity.ok(WebhookResponse(status = "queued"))
    }

    /**
     * Constant-time string comparison to prevent timing attacks.
     */
    private fun secureCompare(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }
}

