package ai.koalla.core.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class WebClientConfig(
    private val koallaProperties: KoallaProperties,
) {
    @Bean
    fun chatwootWebClient(): WebClient =
        WebClient
            .builder()
            .baseUrl(koallaProperties.chatwoot.url)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader("api_access_token", koallaProperties.chatwoot.apiToken)
            .build()

    @Bean
    fun asaasWebClient(): WebClient =
        WebClient
            .builder()
            .baseUrl(koallaProperties.asaas.url)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader("access_token", koallaProperties.asaas.apiKey)
            .build()

    @Bean
    fun openAiWebClient(): WebClient =
        WebClient
            .builder()
            .baseUrl("https://api.openai.com/v1")
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build()
}
