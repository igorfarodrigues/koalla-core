package ai.koalla.core.config

import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class KoallaConfigTest {

    @Nested
    inner class PlansPropertiesTests {
        @Test
        fun `should return starter price for STARTER plan`() {
            val plans = PlansProperties()

            plans.getValue("STARTER") shouldBeEqualTo BigDecimal("29.90")
        }

        @Test
        fun `should return pro price for PRO plan`() {
            val plans = PlansProperties()

            plans.getValue("PRO") shouldBeEqualTo BigDecimal("79.90")
        }

        @Test
        fun `should return business price for BUSINESS plan`() {
            val plans = PlansProperties()

            plans.getValue("BUSINESS") shouldBeEqualTo BigDecimal("99.90")
        }

        @Test
        fun `should be case insensitive`() {
            val plans = PlansProperties()

            plans.getValue("starter") shouldBeEqualTo BigDecimal("29.90")
            plans.getValue("Starter") shouldBeEqualTo BigDecimal("29.90")
            plans.getValue("STARTER") shouldBeEqualTo BigDecimal("29.90")
            plans.getValue("pro") shouldBeEqualTo BigDecimal("79.90")
            plans.getValue("Pro") shouldBeEqualTo BigDecimal("79.90")
            plans.getValue("business") shouldBeEqualTo BigDecimal("99.90")
        }

        @Test
        fun `should return zero for unknown plan`() {
            val plans = PlansProperties()

            plans.getValue("UNKNOWN") shouldBeEqualTo BigDecimal.ZERO
            plans.getValue("FREE") shouldBeEqualTo BigDecimal.ZERO
            plans.getValue("") shouldBeEqualTo BigDecimal.ZERO
        }

        @Test
        fun `should use custom values when provided`() {
            val plans = PlansProperties(
                starter = BigDecimal("19.90"),
                pro = BigDecimal("49.90"),
                business = BigDecimal("149.90")
            )

            plans.getValue("STARTER") shouldBeEqualTo BigDecimal("19.90")
            plans.getValue("PRO") shouldBeEqualTo BigDecimal("49.90")
            plans.getValue("BUSINESS") shouldBeEqualTo BigDecimal("149.90")
        }
    }

    @Nested
    inner class KoallaPropertiesTests {
        @Test
        fun `should have correct default values`() {
            val props = KoallaProperties()

            props.version shouldBeEqualTo "0.3.0"
            props.dbSchema shouldBeEqualTo "koalla"
            props.trialDays shouldBeEqualTo 15
            props.graceHours shouldBeEqualTo 48
            props.messageQueueWaitSeconds shouldBeEqualTo 2.0
            props.agentMaxIterations shouldBeEqualTo 10
            props.memoryWindowLength shouldBeEqualTo 100
            props.koallaWaNumber shouldBeEqualTo "5531936185547"
            props.formattingModel shouldBeEqualTo "gpt-4.1-mini"
        }

        @Test
        fun `should allow custom values`() {
            val props = KoallaProperties(
                version = "1.0.0",
                trialDays = 30,
                graceHours = 72,
                agentMaxIterations = 20
            )

            props.version shouldBeEqualTo "1.0.0"
            props.trialDays shouldBeEqualTo 30
            props.graceHours shouldBeEqualTo 72
            props.agentMaxIterations shouldBeEqualTo 20
        }
    }

    @Nested
    inner class ChatwootPropertiesTests {
        @Test
        fun `should have correct default values`() {
            val props = ChatwootProperties()

            props.url shouldBeEqualTo "https://chatwoot.koalla.ai"
            props.apiToken shouldBeEqualTo ""
            props.accountId shouldBeEqualTo 1
            props.webhookSecret shouldBeEqualTo ""
        }

        @Test
        fun `should allow custom values`() {
            val props = ChatwootProperties(
                url = "https://custom.chatwoot.com",
                apiToken = "token-123",
                accountId = 5,
                webhookSecret = "secret-456"
            )

            props.url shouldBeEqualTo "https://custom.chatwoot.com"
            props.apiToken shouldBeEqualTo "token-123"
            props.accountId shouldBeEqualTo 5
            props.webhookSecret shouldBeEqualTo "secret-456"
        }
    }

    @Nested
    inner class AsaasPropertiesTests {
        @Test
        fun `should have correct default values`() {
            val props = AsaasProperties()

            props.url shouldBeEqualTo "https://api-sandbox.asaas.com"
            props.apiKey shouldBeEqualTo ""
            props.webhookToken shouldBeEqualTo ""
        }

        @Test
        fun `should allow custom values`() {
            val props = AsaasProperties(
                url = "https://api.asaas.com",
                apiKey = "api-key-123",
                webhookToken = "webhook-token-456"
            )

            props.url shouldBeEqualTo "https://api.asaas.com"
            props.apiKey shouldBeEqualTo "api-key-123"
            props.webhookToken shouldBeEqualTo "webhook-token-456"
        }
    }
}

