package ai.koalla.core.tools

import ai.koalla.core.gateway.ChatwootGateway
import kotlinx.coroutines.runBlocking
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Description
import java.util.function.Function

/**
 * Spring AI function beans for human escalation.
 */
@Configuration
class EscalationTools(
    private val contextHolder: ToolContextHolder,
    private val chatwootGateway: ChatwootGateway
) {

    data class EscalateToHumanRequest(
        val summary: String = "",
        val lastMessage: String = ""
    )

    @Bean("escalateToHuman")
    @Description("""
        Escalate conversation to human agent immediately.
        Use when: user is dissatisfied, topic out of scope, user asks for human, or wants to stop messages.
        Parameters:
        - summary: Brief summary of conversation and why it needs human attention
        - lastMessage: User's last message
    """)
    fun escalateToHuman(): Function<EscalateToHumanRequest, String> = Function { req ->
        val ctx = contextHolder.require()
        runBlocking {
            val currentLabels = ctx.labels.toMutableSet()
            currentLabels.add("agente-off")
            chatwootGateway.updateLabels(ctx.accountId, ctx.conversationId, currentLabels.toList())

            val alertConvId = ctx.alertConversationId
            if (alertConvId.isNotBlank()) {
                val alertConvIdInt = alertConvId.toIntOrNull()
                if (alertConvIdInt != null) {
                    val alertMsg = """
                        |🚨 *Escalação humana*
                        |Nome: ${ctx.contactName}
                        |Resumo: ${req.summary}
                        |Última mensagem: ${req.lastMessage}
                    """.trimMargin()
                    chatwootGateway.sendMessage(ctx.accountId, alertConvIdInt, alertMsg)
                }
            }
        }
        "Conversation escalated. A human agent will take over shortly."
    }
}
