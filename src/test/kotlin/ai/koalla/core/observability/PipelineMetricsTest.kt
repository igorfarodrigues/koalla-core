package ai.koalla.core.observability

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeGreaterThan
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class PipelineMetricsTest {
    private lateinit var registry: MeterRegistry
    private lateinit var metrics: PipelineMetrics

    @BeforeEach
    fun setup() {
        registry = SimpleMeterRegistry()
        metrics = PipelineMetrics(registry)
    }

    @Nested
    inner class AgentTimerTests {
        @Test
        fun `should register agent timer`() {
            val timer = metrics.agentTimer

            timer.shouldNotBeNull()
            timer.id.name shouldBeEqualTo "koalla.agent.duration"
        }

        @Test
        fun `should record duration`() {
            val timer = metrics.agentTimer

            timer.record(Runnable { Thread.sleep(10) })

            timer.count() shouldBeEqualTo 1L
            timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS) shouldBeGreaterThan 0.0
        }
    }

    @Nested
    inner class MessageProcessedTests {
        @Test
        fun `should increment counter with default text type`() {
            metrics.messageProcessed()

            val counter =
                registry
                    .find("koalla.messages.processed")
                    .tag("type", "text")
                    .counter()

            counter.shouldNotBeNull()
            counter.count() shouldBeEqualTo 1.0
        }

        @Test
        fun `should increment counter with audio type`() {
            metrics.messageProcessed("audio")

            val counter =
                registry
                    .find("koalla.messages.processed")
                    .tag("type", "audio")
                    .counter()

            counter.shouldNotBeNull()
            counter.count() shouldBeEqualTo 1.0
        }

        @Test
        fun `should increment counter multiple times`() {
            metrics.messageProcessed("text")
            metrics.messageProcessed("text")
            metrics.messageProcessed("text")

            val counter =
                registry
                    .find("koalla.messages.processed")
                    .tag("type", "text")
                    .counter()

            counter.shouldNotBeNull()
            counter.count() shouldBeEqualTo 3.0
        }

        @Test
        fun `should track different types separately`() {
            metrics.messageProcessed("text")
            metrics.messageProcessed("text")
            metrics.messageProcessed("audio")

            val textCounter =
                registry
                    .find("koalla.messages.processed")
                    .tag("type", "text")
                    .counter()
            val audioCounter =
                registry
                    .find("koalla.messages.processed")
                    .tag("type", "audio")
                    .counter()

            textCounter.shouldNotBeNull()
            audioCounter.shouldNotBeNull()
            textCounter.count() shouldBeEqualTo 2.0
            audioCounter.count() shouldBeEqualTo 1.0
        }
    }

    @Nested
    inner class MessageBlockedTests {
        @Test
        fun `should increment counter with label reason`() {
            metrics.messageBlocked("label")

            val counter =
                registry
                    .find("koalla.messages.blocked")
                    .tag("reason", "label")
                    .counter()

            counter.shouldNotBeNull()
            counter.count() shouldBeEqualTo 1.0
        }

        @Test
        fun `should increment counter with inactive reason`() {
            metrics.messageBlocked("inactive")

            val counter =
                registry
                    .find("koalla.messages.blocked")
                    .tag("reason", "inactive")
                    .counter()

            counter.shouldNotBeNull()
            counter.count() shouldBeEqualTo 1.0
        }

        @Test
        fun `should increment counter with unregistered reason`() {
            metrics.messageBlocked("unregistered")

            val counter =
                registry
                    .find("koalla.messages.blocked")
                    .tag("reason", "unregistered")
                    .counter()

            counter.shouldNotBeNull()
            counter.count() shouldBeEqualTo 1.0
        }

        @Test
        fun `should track different reasons separately`() {
            metrics.messageBlocked("label")
            metrics.messageBlocked("label")
            metrics.messageBlocked("inactive")
            metrics.messageBlocked("unregistered")
            metrics.messageBlocked("unregistered")
            metrics.messageBlocked("unregistered")

            val labelCounter =
                registry
                    .find("koalla.messages.blocked")
                    .tag("reason", "label")
                    .counter()
            val inactiveCounter =
                registry
                    .find("koalla.messages.blocked")
                    .tag("reason", "inactive")
                    .counter()
            val unregisteredCounter =
                registry
                    .find("koalla.messages.blocked")
                    .tag("reason", "unregistered")
                    .counter()

            labelCounter!!.count() shouldBeEqualTo 2.0
            inactiveCounter!!.count() shouldBeEqualTo 1.0
            unregisteredCounter!!.count() shouldBeEqualTo 3.0
        }
    }

    @Nested
    inner class PipelineErrorTests {
        @Test
        fun `should increment error counter`() {
            metrics.pipelineError()

            val counter = registry.find("koalla.pipeline.errors").counter()

            counter.shouldNotBeNull()
            counter.count() shouldBeEqualTo 1.0
        }

        @Test
        fun `should increment error counter multiple times`() {
            metrics.pipelineError()
            metrics.pipelineError()
            metrics.pipelineError()

            val counter = registry.find("koalla.pipeline.errors").counter()

            counter.shouldNotBeNull()
            counter.count() shouldBeEqualTo 3.0
        }
    }

    @Nested
    inner class IdempotencyTests {
        @Test
        fun `should be idempotent when calling same metric multiple times`() {
            // Calling the same metric builder multiple times should not create duplicates
            metrics.messageProcessed("text")
            metrics.messageProcessed("text")
            metrics.messageProcessed("text")

            val counters =
                registry
                    .find("koalla.messages.processed")
                    .tag("type", "text")
                    .counters()

            // Should only have one counter registered, not three
            counters.size shouldBeEqualTo 1
            counters.first().count() shouldBeEqualTo 3.0
        }
    }
}
