package ai.koalla.core.client

import ai.koalla.core.config.KoallaProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import org.springframework.web.reactive.function.client.awaitBodyOrNull
import org.springframework.web.reactive.function.client.awaitExchange

/**
 * Chatwoot API client — mirrors all HTTP calls from the Python version.
 */
@Component
class ChatwootClient(
    private val chatwootWebClient: WebClient,
    private val props: KoallaProperties
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private fun basePath(accountId: Int = props.chatwoot.accountId): String =
        "/api/v1/accounts/$accountId"

    /**
     * POST update_last_seen — marks messages as read.
     */
    suspend fun markAsRead(accountId: Int, conversationId: Int) {
        try {
            chatwootWebClient.post()
                .uri("${basePath(accountId)}/conversations/$conversationId/update_last_seen")
                .retrieve()
                .awaitBodyOrNull<Unit>()
        } catch (e: Exception) {
            logger.warn("Failed to mark conversation $conversationId as read: ${e.message}")
        }
    }

    /**
     * POST a text message to a conversation.
     */
    suspend fun sendMessage(accountId: Int, conversationId: Int, content: String): Map<String, Any>? {
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

    /**
     * POST a reaction to a specific message.
     */
    suspend fun sendReaction(
        accountId: Int,
        conversationId: Int,
        messageId: Int,
        emoji: String
    ): Map<String, Any>? {
        val body = mapOf(
            "content" to emoji,
            "content_attributes" to mapOf(
                "in_reply_to" to messageId,
                "is_reaction" to true
            )
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

    /**
     * POST/replace conversation labels.
     */
    suspend fun updateLabels(accountId: Int, conversationId: Int, labels: List<String>) {
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

    /**
     * GET contact details (includes custom_attributes).
     */
    suspend fun getContact(accountId: Int, contactId: Int): Map<String, Any>? {
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

    /**
     * PATCH contact custom attributes.
     */
    suspend fun updateContactAttributes(
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

    /**
     * POST destroy_custom_attributes.
     */
    suspend fun destroyContactAttributes(
        accountId: Int,
        contactId: Int,
        attributes: List<String>
    ) {
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

    /**
     * Download a binary attachment (audio, file) from Chatwoot storage.
     */
    suspend fun downloadAttachment(dataUrl: String): ByteArray? {
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

    /**
     * Search contact by phone number.
     */
    suspend fun searchContactByPhone(accountId: Int, phone: String): Map<String, Any>? {
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

    /**
     * Get contact conversations, sorted by last activity.
     */
    suspend fun getContactConversations(accountId: Int, contactId: Int): List<Map<String, Any>> {
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

    /**
     * Send message to a phone number via the most recent conversation.
     */
    suspend fun sendMessageToPhone(phone: String, content: String): Boolean {
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

