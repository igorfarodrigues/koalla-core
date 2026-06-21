package ai.koalla.core.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import java.math.BigDecimal

@Configuration
@EnableConfigurationProperties(KoallaProperties::class)
class KoallaConfig

@ConfigurationProperties(prefix = "koalla")
data class KoallaProperties(
    val version: String = "0.3.0",
    val dbSchema: String = "koalla",
    val chatwoot: ChatwootProperties = ChatwootProperties(),
    val asaas: AsaasProperties = AsaasProperties(),
    val trialDays: Int = 15,
    val graceHours: Int = 48,
    val messageQueueWaitSeconds: Double = 2.0,
    val agentMaxIterations: Int = 10,
    val memoryWindowLength: Int = 100,
    val plans: PlansProperties = PlansProperties(),
    val alertConversationId: String = "",
    val koallaWaNumber: String = "5531936185547",
    val formattingModel: String = "gpt-4.1-mini",
)

data class ChatwootProperties(
    val url: String = "https://chatwoot.koalla.ai",
    val apiToken: String = "",
    val accountId: Int = 1,
    val webhookSecret: String = "",
)

data class AsaasProperties(
    val url: String = "https://api-sandbox.asaas.com",
    val apiKey: String = "",
    val webhookToken: String = "",
)

data class PlansProperties(
    val starter: BigDecimal = BigDecimal("29.90"),
    val pro: BigDecimal = BigDecimal("79.90"),
    val business: BigDecimal = BigDecimal("99.90"),
) {
    fun getValue(planName: String): BigDecimal =
        when (planName.uppercase()) {
            "STARTER" -> starter
            "PRO" -> pro
            "BUSINESS" -> business
            else -> BigDecimal.ZERO
        }
}
