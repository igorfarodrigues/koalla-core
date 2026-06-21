package ai.koalla.core.gateway

/**
 * Port interface for Chatwoot API operations.
 * Decouples business logic from the concrete HTTP client implementation,
 * making services testable without a live Chatwoot instance.
 */
interface ChatwootGateway {

    suspend fun markAsRead(accountId: Int, conversationId: Int)

    suspend fun sendMessage(accountId: Int, conversationId: Int, content: String): Map<String, Any>?

    suspend fun sendReaction(
        accountId: Int,
        conversationId: Int,
        messageId: Int,
        emoji: String
    ): Map<String, Any>?

    suspend fun updateLabels(accountId: Int, conversationId: Int, labels: List<String>)

    suspend fun getContact(accountId: Int, contactId: Int): Map<String, Any>?

    suspend fun updateContactAttributes(
        accountId: Int,
        contactId: Int,
        customAttributes: Map<String, Any>
    ): Map<String, Any>?

    suspend fun destroyContactAttributes(
        accountId: Int,
        contactId: Int,
        attributes: List<String>
    )

    suspend fun downloadAttachment(dataUrl: String): ByteArray?

    suspend fun searchContactByPhone(accountId: Int, phone: String): Map<String, Any>?

    suspend fun getContactConversations(accountId: Int, contactId: Int): List<Map<String, Any>>

    suspend fun sendMessageToPhone(phone: String, content: String): Boolean
}
