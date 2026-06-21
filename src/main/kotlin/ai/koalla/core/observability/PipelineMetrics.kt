package ai.koalla.core.observability

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component

/**
 * Micrometer metrics for the WhatsApp message pipeline.
 *
 * Counters:
 *   koalla.messages.processed  — every message that reaches the agent (tag: type=text|audio)
 *   koalla.messages.blocked    — messages skipped before the agent (tag: reason=label|inactive|unregistered)
 *   koalla.pipeline.errors     — unhandled exceptions in the pipeline
 *
 * Timers:
 *   koalla.agent.duration      — end-to-end agent + tool execution time
 */
@Component
class PipelineMetrics(private val registry: MeterRegistry) {

    // Micrometer's register() is idempotent — no need for a ConcurrentHashMap cache
    val agentTimer: Timer = Timer.builder("koalla.agent.duration")
        .description("End-to-end agent + tool execution duration")
        .register(registry)

    // ── Counters ──────────────────────────────────────────────────────────────

    fun messageProcessed(type: String = "text") {
        Counter.builder("koalla.messages.processed")
            .description("Messages delivered to the AI agent")
            .tag("type", type)
            .register(registry)
            .increment()
    }

    fun messageBlocked(reason: String) {
        Counter.builder("koalla.messages.blocked")
            .description("Messages skipped before reaching the agent")
            .tag("reason", reason)
            .register(registry)
            .increment()
    }

    fun pipelineError() {
        Counter.builder("koalla.pipeline.errors")
            .description("Unhandled exceptions in the message pipeline")
            .register(registry)
            .increment()
    }
}
