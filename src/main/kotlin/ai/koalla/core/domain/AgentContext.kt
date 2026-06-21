package ai.koalla.core.domain

import java.util.UUID

/**
 * Immutable context passed to the AI agent and tools during message processing.
 * Carries all conversation state needed for tool execution without relying on thread-locals
 * beyond what ToolContextHolder manages within a pinned Dispatchers.IO coroutine.
 */
data class AgentContext(
    val userId: UUID,
    val waId: String,
    val accountId: Int,
    val conversationId: Int,
    val contactId: Int,
    val messageId: Int,
    val contactName: String,
    val labels: List<String>,
    val contactCustomAttributes: Map<String, Any>,
    val alertConversationId: String,
)
