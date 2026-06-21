package ai.koalla.core.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * DTOs for Chatwoot Webhook payloads.
 * Using @JsonIgnoreProperties to handle unknown fields gracefully.
 */

@JsonIgnoreProperties(ignoreUnknown = true)
data class ChatwootSender(
    val id: Int? = null,
    val name: String? = null,
    @JsonProperty("phone_number")
    val phoneNumber: String? = null,
    val email: String? = null,
    @JsonProperty("custom_attributes")
    val customAttributes: Map<String, Any> = emptyMap(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ChatwootAttachment(
    val id: Int? = null,
    @JsonProperty("file_type")
    val fileType: String? = null,
    @JsonProperty("data_url")
    val dataUrl: String? = null,
    val meta: Map<String, Any> = emptyMap(),
) {
    val isRecordedAudio: Boolean
        get() = meta["is_recorded_audio"] as? Boolean ?: false
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class ChatwootContactInbox(
    @JsonProperty("contact_id")
    val contactId: Int? = null,
    @JsonProperty("source_id")
    val sourceId: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ChatwootConversationMeta(
    val sender: ChatwootSender = ChatwootSender(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ChatwootConversation(
    val id: Int? = null,
    val labels: List<String> = emptyList(),
    @JsonProperty("custom_attributes")
    val customAttributes: Map<String, Any> = emptyMap(),
    @JsonProperty("contact_inbox")
    val contactInbox: ChatwootContactInbox = ChatwootContactInbox(),
    val meta: ChatwootConversationMeta = ChatwootConversationMeta(),
    val messages: List<Map<String, Any>> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ChatwootAccount(
    val id: Int? = null,
    val name: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ChatwootWebhookBody(
    val id: Int? = null,
    val account: ChatwootAccount = ChatwootAccount(),
    val content: String? = null,
    @JsonProperty("message_type")
    val messageType: String? = null,
    @JsonProperty("created_at")
    val createdAt: String? = null,
    val sender: ChatwootSender = ChatwootSender(),
    val conversation: ChatwootConversation = ChatwootConversation(),
    val attachments: List<ChatwootAttachment> = emptyList(),
    val event: String? = null,
)

// Response DTOs
data class WebhookResponse(
    val status: String,
    val event: String? = null,
)
