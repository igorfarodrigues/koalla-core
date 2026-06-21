package ai.koalla.core.service

import ai.koalla.core.agent.KoallaAgent
import ai.koalla.core.client.ChatwootClient
import ai.koalla.core.config.KoallaProperties
import ai.koalla.core.dto.ChatwootWebhookBody
import ai.koalla.core.entity.ConversationStatus
import ai.koalla.core.entity.MessageQueue
import ai.koalla.core.repository.*
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
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
    private val chatwootClient: ChatwootClient,
    private val koallaAgent: KoallaAgent,
    private val props: KoallaProperties
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        // Labels that prevent the bot from responding
        val BLOCKED_LABELS = setOf("agente-off", "gestor", "testando-agente")
    }

    /**
     * Entry point called by the webhook controller.
     * Mirrors the full n8n flow end-to-end.
     */
    @Async
    suspend fun processWebhook(body: ChatwootWebhookBody) {
        try {
            doProcessWebhook(body)
        } catch (e: Exception) {
            logger.error("Error processing webhook: ${e.message}", e)
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

        // Audio flag from first attachment
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

        val sessionId = waId // mirrors n8n session key

        // ── 2. Route: /reset ───────────────────────────────────────────────────
        if (content.lowercase().trim() == "/reset") {
            resetConversation(sessionId, body)
            return
        }

        // ── 3. Route: /teste ──────────────────────────────────────────────────
        if (content.lowercase().trim() == "/teste" && conversationId != null) {
            val newLabels = (labels + "testando-agente").toList()
            chatwootClient.updateLabels(accountId, conversationId, newLabels)
            chatwootClient.sendMessage(accountId, conversationId, "Modo de teste habilitado.")
            return
        }

        // ── 4. Filter: only process valid incoming messages ────────────────────
        if (messageType != "incoming") return
        if (labels.intersect(BLOCKED_LABELS).isNotEmpty()) return

        // ── 5. Gate: user must be registered and active ────────────────────────
        val user = userRepository.findByWaId(waId)

        if (user == null) {
            if (conversationId != null) {
                chatwootClient.sendMessage(
                    accountId,
                    conversationId,
                    "👋 Olá! Para usar o Koalla, cadastre-se em *koalla.ai*\n\n" +
                    "É rápido e o primeiro acesso é grátis por 7 dias 🐨"
                )
            }
            return
        }

        if (!user.isActive) {
            if (conversationId != null) {
                chatwootClient.sendMessage(
                    accountId,
                    conversationId,
                    "Sua assinatura está inativa. Para continuar usando o Koalla, " +
                    "acesse *koalla.ai/planos* e escolha um plano 🐨"
                )
            }
            return
        }

        // ── 6. Resolve message text (text / file / audio) ─────────────────────
        var resolvedContent = content
        var fileInfo = ""

        if (attachment != null && !isAudio && attachment.fileType != null) {
            fileInfo = "\n<usuário enviou um arquivo do tipo '${attachment.fileType}'>"
        }

        if (isAudio && attachment?.dataUrl != null) {
            val audioBytes = chatwootClient.downloadAttachment(attachment.dataUrl)
            if (audioBytes != null) {
                resolvedContent = koallaAgent.transcribeAudio(audioBytes)
            }
        } else if (resolvedContent.isEmpty() && fileInfo.isNotEmpty()) {
            resolvedContent = fileInfo
        } else {
            resolvedContent = (resolvedContent) + fileInfo
        }

        // ── 7. Enqueue message ────────────────────────────────────────────────
        enqueueMessage(sessionId, messageId, resolvedContent)

        // ── 8. Debounce: wait then check if this is still the latest message ──
        delay((props.messageQueueWaitSeconds * 1000).toLong())

        val queue = fetchQueue(sessionId)
        if (queue.isEmpty() || queue.last().messageId != messageId) {
            // A newer message arrived — let that execution handle it
            return
        }

        // ── 9. Conversation lock: wait if agent is already responding ─────────
        repeat(5) {
            val status = getStatus(sessionId)
            if (status == null || !status.lockConversa) {
                return@repeat
            }
            delay((props.messageQueueWaitSeconds * 10 * 1000).toLong())
        }

        // Check one more time
        val finalStatus = getStatus(sessionId)
        if (finalStatus?.lockConversa == true) {
            // Gave up waiting
            return
        }

        // ── 10. Lock conversation & clear queue ───────────────────────────────
        lockConversation(sessionId)
        clearQueue(sessionId)

        // Collect all queued messages into one
        val combinedMessage = queue.joinToString("\n") { it.message }

        // ── 11. Mark as read ──────────────────────────────────────────────────
        if (conversationId != null) {
            chatwootClient.markAsRead(accountId, conversationId)
        }

        // ── 12. Build agent context ───────────────────────────────────────────
        val contactData = if (contactId != null) {
            try {
                chatwootClient.getContact(accountId, contactId)
            } catch (e: Exception) {
                null
            }
        } else null

        @Suppress("UNCHECKED_CAST")
        val contactCustomAttrs = (contactData?.get("payload") as? Map<String, Any>)
            ?.get("custom_attributes") as? Map<String, Any> ?: contactAttrs

        val agentContext = AgentContext(
            userId = user.id!!,
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
        val output = try {
            koallaAgent.runAgent(combinedMessage, sessionId, agentContext)
        } catch (e: Exception) {
            unlockConversation(sessionId)
            throw e
        }

        if (output.isNullOrEmpty() || output == "Agent stopped due to max iterations.") {
            unlockConversation(sessionId)
            return
        }

        // ── 14. Format output for WhatsApp ────────────────────────────────────
        val formatted = koallaAgent.formatForWhatsApp(output)

        // ── 15. Send response ─────────────────────────────────────────────────
        if (conversationId != null) {
            val chunks = splitMessage(formatted)
            for (chunk in chunks) {
                chatwootClient.sendMessage(accountId, conversationId, chunk)
            }
        }

        // ── 16. Unlock ────────────────────────────────────────────────────────
        unlockConversation(sessionId)
    }

    // ── Helper methods ────────────────────────────────────────────────────────

    @Transactional
    fun enqueueMessage(waId: String, messageId: String, message: String) {
        messageQueueRepository.save(
            MessageQueue(
                waId = waId,
                messageId = messageId,
                message = message,
                timestamp = OffsetDateTime.now()
            )
        )
    }

    fun fetchQueue(waId: String): List<MessageQueue> {
        return messageQueueRepository.findByWaIdOrderByTimestampAsc(waId)
    }

    @Transactional
    fun clearQueue(waId: String) {
        messageQueueRepository.deleteByWaId(waId)
    }

    fun getStatus(sessionId: String): ConversationStatus? {
        return conversationStatusRepository.findBySessionId(sessionId)
    }

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
                ConversationStatus(
                    sessionId = sessionId,
                    lockConversa = true,
                    aguardandoFollowup = true,
                    numeroFollowup = 0
                )
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
                ConversationStatus(
                    sessionId = sessionId,
                    lockConversa = false,
                    aguardandoFollowup = false,
                    numeroFollowup = 0
                )
            )
        }
    }

    @Transactional
    suspend fun resetConversation(sessionId: String, body: ChatwootWebhookBody) {
        val accountId = body.account.id
        val conversationId = body.conversation.id
        val contactId = body.conversation.contactInbox.contactId

        // Clear chat history (memory)
        chatHistoryRepository.deleteBySessionId(sessionId)

        // Clear queue
        clearQueue(sessionId)

        // Reset conversation status
        unlockConversation(sessionId)

        // Chatwoot: remove custom attributes
        if (accountId != null && contactId != null) {
            chatwootClient.destroyContactAttributes(
                accountId, contactId,
                listOf("preferencia_audio_texto", "asaas_id_cliente", "asaas_id_cobranca", "asaas_status_cobranca")
            )
        }

        // Chatwoot: remove agente-off label
        if (accountId != null && conversationId != null) {
            val currentLabels = body.conversation.labels
            val cleanLabels = currentLabels.filter { it != "agente-off" }
            chatwootClient.updateLabels(accountId, conversationId, cleanLabels)
            chatwootClient.sendMessage(accountId, conversationId, "Memória resetada.")
        }
    }

    /**
     * Split a long message into WhatsApp-safe chunks at sentence boundaries.
     */
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
            if (splitAt == -1) {
                splitAt = remaining.lastIndexOf(". ", maxLen)
            }
            if (splitAt == -1) {
                splitAt = maxLen
            }

            chunks.add(remaining.substring(0, splitAt).trim())
            remaining = remaining.substring(splitAt).trim()
        }

        return chunks
    }
}

data class AgentContext(
    val userId: java.util.UUID,
    val waId: String,
    val accountId: Int,
    val conversationId: Int,
    val contactId: Int,
    val messageId: Int,
    val contactName: String,
    val labels: List<String>,
    val contactCustomAttributes: Map<String, Any>,
    val alertConversationId: String
)

