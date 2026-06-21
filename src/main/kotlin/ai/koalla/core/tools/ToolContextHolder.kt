package ai.koalla.core.tools

import ai.koalla.core.service.AgentContext
import org.springframework.stereotype.Component

/**
 * Thread-safe holder for agent context during tool execution.
 * Spring AI tools need access to conversation context (userId, conversationId, etc.)
 * which is set before agent invocation and cleared after.
 */
@Component
class ToolContextHolder {

    private val contextHolder = ThreadLocal<AgentContext>()

    fun set(context: AgentContext) {
        contextHolder.set(context)
    }

    fun get(): AgentContext? = contextHolder.get()

    fun require(): AgentContext = contextHolder.get()
        ?: throw IllegalStateException("Agent context not set. Ensure context is set before tool invocation.")

    fun clear() {
        contextHolder.remove()
    }
}

