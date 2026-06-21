package ai.koalla.core.client

import ai.koalla.core.config.KoallaProperties
import ai.koalla.core.gateway.ChatwootGateway
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import org.springframework.web.reactive.function.client.awaitBodyOrNull

/**
 * Chatwoot HTTP adapter — implements [ChatwootGateway].
 * All gateway methods correspond 1-to-1 with Chatwoot API v1 calls.
 */
@Component
class ChatwootClient(
    private val chatwootWebClient: WebClient,
    private val props: KoallaProperties
) : ChatwootGateway {

    private val logger = LoggerFactory.getLogger(javaClass)

    private fun basePath(accountId: Int = props.chatwoot.accountId): String =
        "/api/v1/accounts/$accountId"

    override suspend fun markAsRead(accountId: Int, conversationId: Int) {
        try {
            chatwootWebClient.post()
                .uri("${basePath(accountId)}/conversations/$conversationId/update_last_seen")
                .retrieve()
                .awaitBodyOrNull<Unit>()
        } catch (e: Exception) {
            logger.warn("Failed to mark conversation $conversationId as read: ${e.message}")
        }
    }

    override suspend fun sendMessage(accountId: Int, conversationId: Int, content: String): Map<String, Any>? {
        return try {
            chatwootWebClient.post()
                .uri("${basePath(accountId)}/conversations/$conversationId/messages")
                .bodyValue(mapOf("content" to content))
                .retrieve()
                .awaitBody<Map<String, Any>>()
        } catch (e: Exception) {
            logger.error("Failed to send message to conversation $conversationId: ${e.message}")
            null
        }
    }

    override suspend fun sendReaction(
        accountId: Int,
        conversationId: Int,
        messageId: Int,
        emoji: String
    ): Map<String, Any>? {
        val body = mapOf(
            "content" to emoji,
            "content_attributes" to mapOf("in_reply_to" to messageId, "is_reaction" to true)
        )
        return try {
            chatwootWebClient.post()
                .uri("${basePath(accountId)}/conversations/$conversationId/messages")
                .bodyValue(body)
                .retrieve()
                .awaitBody<Map<String, Any>>()
        } catch (e: Exception) {
            logger.error("Failed to send reaction: ${e.message}")
            null
        }
    }

    override suspend fun updateLabels(accountId: Int, conversationId: Int, labels: List<String>) {
        try {
            chatwootWebClient.post()
                .uri("${basePath(accountId)}/conversations/$conversationId/labels")
                .bodyValue(mapOf("labels" to labels))
                .retrieve()
                .awaitBodyOrNull<Unit>()
        } catch (e: Exception) {
            logger.warn("Failed to update labels: ${e.message}")
        }
    }

    override suspend fun getContact(accountId: Int, contactId: Int): Map<String, Any>? {
        return try {
            chatwootWebClient.get()
                .uri("${basePath(accountId)}/contacts/$contactId")
                .retrieve()
                .awaitBody<Map<String, Any>>()
        } catch (e: Exception) {
            logger.warn("Failed to get contact $contactId: ${e.message}")
            null
        }
    }

    override suspend fun updateContactAttributes(
        accountId: Int,
        contactId: Int,
        customAttributes: Map<String, Any>
    ): Map<String, Any>? {
        return try {
            chatwootWebClient.patch()
                .uri("${basePath(accountId)}/contacts/$contactId")
                .bodyValue(mapOf("custom_attributes" to customAttributes))
                .retrieve()
                .awaitBody<Map<String, Any>>()
        } catch (e: Exception) {
            logger.error("Failed to update contact attributes: ${e.message}")
            null
        }
    }

    override suspend fun destroyContactAttributes(accountId: Int, contactId: Int, attributes: List<String>) {
        try {
            chatwootWebClient.post()
                .uri("${basePath(accountId)}/contacts/$contactId/destroy_custom_attributes")
                .bodyValue(mapOf("custom_attributes" to attributes))
                .retrieve()
                .awaitBodyOrNull<Unit>()
        } catch (e: Exception) {
            logger.warn("Failed to destroy contact attributes: ${e.message}")
        }
    }

    override suspend fun downloadAttachment(dataUrl: String): ByteArray? {
        return try {
            chatwootWebClient.get()
                .uri(dataUrl)
                .retrieve()
                .awaitBody<ByteArray>()
        } catch (e: Exception) {
            logger.error("Failed to download attachment: ${e.message}")
            null
        }
    }

    override suspend fun searchContactByPhone(accountId: Int, phone: String): Map<String, Any>? {
        return try {
            val response = chatwootWebClient.get()
                .uri("${basePath(accountId)}/contacts/search?q=$phone&page=1")
                .retrieve()
                .awaitBody<Map<String, Any>>()

            @Suppress("UNCHECKED_CAST")
            val payload = response["payload"] as? List<Map<String, Any>>
            payload?.firstOrNull()
        } catch (e: Exception) {
            logger.warn("Failed to search contact by phone $phone: ${e.message}")
            null
        }
    }

    override suspend fun getContactConversations(accountId: Int, contactId: Int): List<Map<String, Any>> {
        return try {
            val response = chatwootWebClient.get()
                .uri("${basePath(accountId)}/contacts/$contactId/conversations")
                .retrieve()
                .awaitBody<Map<String, Any>>()

            @Suppress("UNCHECKED_CAST")
            val payload = response["payload"] as? List<Map<String, Any>> ?: emptyList()
            payload.sortedByDescending { it["last_activity_at"] as? Long ?: 0L }
        } catch (e: Exception) {
            logger.warn("Failed to get contact conversations: ${e.message}")
            emptyList()
        }
    }

    override suspend fun sendMessageToPhone(phone: String, content: String): Boolean {
        val accountId = props.chatwoot.accountId
        val contact = searchContactByPhone(accountId, phone) ?: return false
        val contactId = (contact["id"] as? Number)?.toInt() ?: return false
        val conversations = getContactConversations(accountId, contactId)
        if (conversations.isEmpty()) return false
        val conversationId = (conversations[0]["id"] as? Number)?.toInt() ?: return false
        sendMessage(accountId, conversationId, content)
        return true
    }
}
