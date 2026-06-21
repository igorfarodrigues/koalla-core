package ai.koalla.core.tools

import ai.koalla.core.gateway.ChatwootGateway
import kotlinx.coroutines.runBlocking
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Description
import java.util.function.Function

/**
 * Spring AI function beans for Chatwoot messaging operations.
 */
@Configuration
class ChatwootTools(
    private val contextHolder: ToolContextHolder,
    private val chatwootGateway: ChatwootGateway
) {

    data class SendTextRequest(val content: String = "")

    @Bean("sendText")
    @Description("""
        Send a text message to the user via Chatwoot.
        Use for links, phone numbers, PIX keys, or formatted info.
        NEVER include the sent data again in your output after calling this tool.
        Parameters:
        - content: The text message to send
    """)
    fun sendText(): Function<SendTextRequest, String> = Function { req ->
        val ctx = contextHolder.require()
        runBlocking {
            chatwootGateway.sendMessage(ctx.accountId, ctx.conversationId, req.content)
        }
        "Message sent."
    }

    data class ReactToMessageRequest(val emoji: String = "👍")

    @Bean("reactToMessage")
    @Description("""
        Send an emoji reaction to the user's last message.
        Use max 3 times per conversation. NEVER use multiple times in a row.
        Parameters:
        - emoji: Allowed: 😀 ❤️ 👍 👀 ✅
    """)
    fun reactToMessage(): Function<ReactToMessageRequest, String> = Function { req ->
        val ctx = contextHolder.require()
        val allowedEmojis = setOf("😀", "❤️", "👍", "👀", "✅")
        if (req.emoji !in allowedEmojis) {
            return@Function "Invalid emoji. Allowed: ${allowedEmojis.joinToString(" ")}"
        }
        runBlocking {
            chatwootGateway.sendReaction(ctx.accountId, ctx.conversationId, ctx.messageId, req.emoji)
        }
        "Reaction sent."
    }

    data class SetResponsePreferenceRequest(val preference: String = "texto")

    @Bean("setResponsePreference")
    @Description("""
        Save user's preference for audio or text responses.
        Only use when user explicitly requests.
        Parameters:
        - preference: 'audio' or 'texto'
    """)
    fun setResponsePreference(): Function<SetResponsePreferenceRequest, String> = Function { req ->
        val ctx = contextHolder.require()
        val validPrefs = setOf("audio", "texto")
        if (req.preference.lowercase() !in validPrefs) {
            return@Function "Invalid preference. Use 'audio' or 'texto'."
        }
        val currentAttrs = ctx.contactCustomAttributes.toMutableMap()
        currentAttrs["preferencia_audio_texto"] = req.preference.lowercase()
        runBlocking {
            chatwootGateway.updateContactAttributes(ctx.accountId, ctx.contactId, currentAttrs)
        }
        "Preference set to '${req.preference}'."
    }

    data class SendCancellationAlertRequest(val message: String = "")

    @Bean("sendCancellationAlert")
    @Description("""
        Send a cancellation alert to the manager's conversation.
        Parameters:
        - message: Alert with name, date/time, reason and notes
    """)
    fun sendCancellationAlert(): Function<SendCancellationAlertRequest, String> = Function { req ->
        val ctx = contextHolder.require()
        val alertConvId = ctx.alertConversationId
        if (alertConvId.isBlank()) {
            return@Function "Alert conversation not configured."
        }
        val alertConvIdInt = alertConvId.toIntOrNull()
            ?: return@Function "Invalid alert conversation ID."
        runBlocking {
            chatwootGateway.sendMessage(ctx.accountId, alertConvIdInt, req.message)
        }
        "Alert sent."
    }
}
