package ai.koalla.core.service

import ai.koalla.core.agent.KoallaAgent
import ai.koalla.core.config.KoallaProperties
import ai.koalla.core.domain.AgentContext
import ai.koalla.core.dto.ChatwootWebhookBody
import ai.koalla.core.entity.ConversationStatus
import ai.koalla.core.entity.MessageQueue
import ai.koalla.core.gateway.ChatwootGateway
import ai.koalla.core.observability.PipelineMetrics
import ai.koalla.core.repository.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

/**
 * Core message processing pipeline.
 * Mirrors the full n8n workflow execution order:
 *   Info → Reset/Teste → Filter → Type → Queue → Debounce → Lock → Agent → Response
 */
@Service
class MessagePipelineService(
    private val userRepository: UserRepository,
    private val messageQueueRepository: MessageQueueRepository,
    private val conversationStatusRepository: ConversationStatusRepository,
    private val chatHistoryRepository: ChatHistoryRepository,
    private val chatwootGateway: ChatwootGateway,
    private val koallaAgent: KoallaAgent,
    private val audioService: AudioService,
    private val props: KoallaProperties,
    private val applicationScope: CoroutineScope,
    private val metrics: PipelineMetrics
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        val BLOCKED_LABELS = setOf("agente-off", "gestor", "testando-agente")
    }

    /**
     * Entry point called by the webhook controller.
     * Launches processing in the application CoroutineScope so the HTTP response
     * returns immediately (non-blocking) while the pipeline runs in the background.
     */
    fun processWebhook(body: ChatwootWebhookBody) {
        applicationScope.launch {
            try {
                doProcessWebhook(body)
            } catch (e: Exception) {
                metrics.pipelineError()
                logger.error("Error processing webhook: ${e.message}", e)
            }
        }
    }

    private suspend fun doProcessWebhook(body: ChatwootWebhookBody) {
        // ── 1. Extract info ────────────────────────────────────────────────────
        val messageType = body.messageType ?: ""
        val labels = body.conversation.labels.toSet()
        val waId = body.conversation.meta.sender.phoneNumber ?: ""
        val name = body.sender.name
        val content = body.content ?: ""
        val messageId = body.id?.toString() ?: ""
        val accountId = body.account.id ?: props.chatwoot.accountId
        val conversationId = body.conversation.id
        val contactId = body.conversation.contactInbox.contactId
        val contactAttrs = body.conversation.customAttributes

        // Structured logging context
        MDC.put("waId", waId)
        MDC.put("conversationId", conversationId?.toString() ?: "unknown")

        try {
            doProcessWithContext(
                messageType, labels, waId, name, content, messageId,
                accountId, conversationId, contactId, contactAttrs, body
            )
        } finally {
            MDC.remove("waId")
            MDC.remove("conversationId")
        }
    }

    @Suppress("LongParameterList")
    private suspend fun doProcessWithContext(
        messageType: String,
        labels: Set<String>,
        waId: String,
        name: String?,
        content: String,
        messageId: String,
        accountId: Int,
        conversationId: Int?,
        contactId: Int?,
        contactAttrs: Map<String, Any>,
        body: ChatwootWebhookBody
    ) {
        val sessionId = waId

        // ── 2. Route: /reset ───────────────────────────────────────────────────
        if (content.lowercase().trim() == "/reset") {
            resetConversation(sessionId, body)
            return
        }

        // ── 3. Route: /teste ──────────────────────────────────────────────────
        if (content.lowercase().trim() == "/teste" && conversationId != null) {
            val newLabels = (labels + "testando-agente").toList()
            chatwootGateway.updateLabels(accountId, conversationId, newLabels)
            chatwootGateway.sendMessage(accountId, conversationId, "Modo de teste habilitado.")
            return
        }

        // ── 4. Filter: only incoming messages, no blocked labels ──────────────
        if (messageType != "incoming") return
        if (labels.intersect(BLOCKED_LABELS).isNotEmpty()) {
            metrics.messageBlocked("label")
            return
        }

        // ── 5. Gate: user must be registered and active ────────────────────────
        val userEntity = userRepository.findByWaId(waId)

        if (userEntity == null) {
            metrics.messageBlocked("unregistered")
            if (conversationId != null) {
                chatwootGateway.sendMessage(
                    accountId, conversationId,
                    "👋 Olá! Para usar o Koalla, cadastre-se em *koalla.ai*\n\n" +
                    "É rápido e o primeiro acesso é grátis por 7 dias 🐨"
                )
            }
            return
        }

        if (!userEntity.isActive) {
            metrics.messageBlocked("inactive")
            if (conversationId != null) {
                chatwootGateway.sendMessage(
                    accountId, conversationId,
                    "Sua assinatura está inativa. Para continuar usando o Koalla, " +
                    "acesse *koalla.ai/planos* e escolha um plano 🐨"
                )
            }
            return
        }

        // ── 6. Resolve message text (text / file / audio) ─────────────────────
        var isAudio = false
        val attachment = body.attachments.firstOrNull()
        if (attachment != null) {
            isAudio = attachment.isRecordedAudio
        } else if (body.conversation.messages.isNotEmpty()) {
            val msgs = body.conversation.messages
            val att = msgs.firstOrNull()?.get("attachments") as? List<*>
            if (!att.isNullOrEmpty()) {
                @Suppress("UNCHECKED_CAST")
                val firstAtt = att.first() as? Map<String, Any>
                @Suppress("UNCHECKED_CAST")
                val meta = firstAtt?.get("meta") as? Map<String, Any>
                isAudio = meta?.get("is_recorded_audio") as? Boolean ?: false
            }
        }

        var resolvedContent = content
        var fileInfo = ""

        if (attachment != null && !isAudio && attachment.fileType != null) {
            fileInfo = "\n<usuário enviou um arquivo do tipo '${attachment.fileType}'>"
        }

        if (isAudio && attachment?.dataUrl != null) {
            val audioBytes = chatwootGateway.downloadAttachment(attachment.dataUrl)
            if (audioBytes != null) {
                resolvedContent = audioService.transcribe(audioBytes)
            }
        } else if (resolvedContent.isEmpty() && fileInfo.isNotEmpty()) {
            resolvedContent = fileInfo
        } else {
            resolvedContent = resolvedContent + fileInfo
        }

        // ── 7. Enqueue message ────────────────────────────────────────────────
        enqueueMessage(sessionId, messageId, resolvedContent)

        // ── 8. Debounce ───────────────────────────────────────────────────────
        delay((props.messageQueueWaitSeconds * 1000).toLong())

        val queue = fetchQueue(sessionId)
        if (queue.isEmpty() || queue.last().messageId != messageId) {
            return
        }

        // ── 9. Lock: wait if agent is already responding ──────────────────────
        var lockAttempts = 0
        while (lockAttempts < 5 && getStatus(sessionId)?.lockConversa == true) {
            delay((props.messageQueueWaitSeconds * 10 * 1000).toLong())
            lockAttempts++
        }

        if (getStatus(sessionId)?.lockConversa == true) return

        // ── 10. Lock & clear queue ────────────────────────────────────────────
        lockConversation(sessionId)
        clearQueue(sessionId)

        val combinedMessage = queue.joinToString("\n") { it.message }

        // ── 11. Mark as read ──────────────────────────────────────────────────
        if (conversationId != null) {
            chatwootGateway.markAsRead(accountId, conversationId)
        }

        // ── 12. Build agent context ───────────────────────────────────────────
        val contactData = if (contactId != null) {
            try { chatwootGateway.getContact(accountId, contactId) } catch (e: Exception) { null }
        } else null

        @Suppress("UNCHECKED_CAST")
        val contactCustomAttrs = (contactData?.get("payload") as? Map<String, Any>)
            ?.get("custom_attributes") as? Map<String, Any> ?: contactAttrs

        val agentContext = AgentContext(
            userId = userEntity.id!!,
            waId = waId,
            accountId = accountId,
            conversationId = conversationId ?: 0,
            contactId = contactId ?: 0,
            messageId = messageId.toIntOrNull() ?: 0,
            contactName = name ?: waId,
            labels = labels.toList(),
            contactCustomAttributes = contactCustomAttrs,
            alertConversationId = props.alertConversationId
        )

        // ── 13. Run agent ─────────────────────────────────────────────────────
        val msgType = if (isAudio) "audio" else "text"
        metrics.messageProcessed(msgType)

        val agentStart = System.nanoTime()
        val output = try {
            koallaAgent.runAgent(combinedMessage, sessionId, agentContext)
        } catch (e: Exception) {
            unlockConversation(sessionId)
            throw e
        } finally {
            metrics.agentTimer().record(System.nanoTime() - agentStart, java.util.concurrent.TimeUnit.NANOSECONDS)
        }

        if (output.isNullOrEmpty() || output == "Agent stopped due to max iterations.") {
            unlockConversation(sessionId)
            return
        }

        // ── 14. Format output ─────────────────────────────────────────────────
        val formatted = koallaAgent.formatForWhatsApp(output)

        // ── 15. Send response ─────────────────────────────────────────────────
        if (conversationId != null) {
            splitMessage(formatted).forEach { chunk ->
                chatwootGateway.sendMessage(accountId, conversationId, chunk)
            }
        }

        // ── 16. Unlock ────────────────────────────────────────────────────────
        unlockConversation(sessionId)
    }

    // ── Helper methods ────────────────────────────────────────────────────────

    @Transactional
    fun enqueueMessage(waId: String, messageId: String, message: String) {
        messageQueueRepository.save(
            MessageQueue(waId = waId, messageId = messageId, message = message, timestamp = OffsetDateTime.now())
        )
    }

    fun fetchQueue(waId: String): List<MessageQueue> =
        messageQueueRepository.findByWaIdOrderByTimestampAsc(waId)

    @Transactional
    fun clearQueue(waId: String) {
        messageQueueRepository.deleteByWaId(waId)
    }

    fun getStatus(sessionId: String): ConversationStatus? =
        conversationStatusRepository.findBySessionId(sessionId)

    @Transactional
    fun lockConversation(sessionId: String) {
        val existing = conversationStatusRepository.findBySessionId(sessionId)
        if (existing != null) {
            existing.lockConversa = true
            existing.aguardandoFollowup = true
            existing.numeroFollowup = 0
            existing.updatedAt = OffsetDateTime.now()
            conversationStatusRepository.save(existing)
        } else {
            conversationStatusRepository.save(
                ConversationStatus(sessionId = sessionId, lockConversa = true, aguardandoFollowup = true, numeroFollowup = 0)
            )
        }
    }

    @Transactional
    fun unlockConversation(sessionId: String) {
        val existing = conversationStatusRepository.findBySessionId(sessionId)
        if (existing != null) {
            existing.lockConversa = false
            existing.aguardandoFollowup = false
            existing.updatedAt = OffsetDateTime.now()
            conversationStatusRepository.save(existing)
        } else {
            conversationStatusRepository.save(
                ConversationStatus(sessionId = sessionId, lockConversa = false, aguardandoFollowup = false, numeroFollowup = 0)
            )
        }
    }

    @Transactional
    suspend fun resetConversation(sessionId: String, body: ChatwootWebhookBody) {
        val accountId = body.account.id
        val conversationId = body.conversation.id
        val contactId = body.conversation.contactInbox.contactId

        chatHistoryRepository.deleteBySessionId(sessionId)
        clearQueue(sessionId)
        unlockConversation(sessionId)

        if (accountId != null && contactId != null) {
            chatwootGateway.destroyContactAttributes(
                accountId, contactId,
                listOf("preferencia_audio_texto", "asaas_id_cliente", "asaas_id_cobranca", "asaas_status_cobranca")
            )
        }

        if (accountId != null && conversationId != null) {
            val cleanLabels = body.conversation.labels.filter { it != "agente-off" }
            chatwootGateway.updateLabels(accountId, conversationId, cleanLabels)
            chatwootGateway.sendMessage(accountId, conversationId, "Memória resetada.")
        }
    }

    fun splitMessage(text: String, maxLen: Int = 4096): List<String> {
        if (text.length <= maxLen) return listOf(text)

        val chunks = mutableListOf<String>()
        var remaining = text

        while (remaining.isNotEmpty()) {
            if (remaining.length <= maxLen) {
                chunks.add(remaining)
                break
            }
            var splitAt = remaining.lastIndexOf("\n\n", maxLen)
            if (splitAt == -1) splitAt = remaining.lastIndexOf(". ", maxLen)
            if (splitAt == -1) splitAt = maxLen

            chunks.add(remaining.substring(0, splitAt).trim())
            remaining = remaining.substring(splitAt).trim()
        }

        return chunks
    }
}
