package ai.koalla.core.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * OpenAPI/Swagger configuration for API documentation.
 *
 * Access points:
 * - Swagger UI: /swagger-ui.html
 * - OpenAPI JSON: /v3/api-docs
 * - OpenAPI YAML: /v3/api-docs.yaml
 */
@Configuration
class OpenApiConfig(
    @Value("\${koalla.version:0.3.0}") private val version: String,
) {
    @Bean
    fun customOpenAPI(): OpenAPI =
        OpenAPI()
            .info(apiInfo())
            .servers(
                listOf(
                    Server().url("/").description("Current Server"),
                    Server().url("http://localhost:8080").description("Local Development"),
                    Server().url("https://api.koalla.ai").description("Production"),
                ),
            ).components(
                Components()
                    .addSecuritySchemes(
                        "basicAuth",
                        SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("basic")
                            .description("Basic authentication for admin endpoints"),
                    ).addSecuritySchemes(
                        "bearerAuth",
                        SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT")
                            .description("JWT token authentication"),
                    ),
            ).addSecurityItem(SecurityRequirement().addList("basicAuth"))

    private fun apiInfo(): Info =
        Info()
            .title("Koalla API")
            .description(
                """
                API backend do Koalla, assistente financeira pessoal via WhatsApp.
                
                ## Funcionalidades
                - Recebe webhooks do Chatwoot para processar mensagens WhatsApp
                - Gerencia usuários e transações financeiras
                - Processa mensagens com agente LLM (Spring AI + OpenAI)
                - Integração com Asaas para cobranças PIX
                
                ## Autenticação
                - Endpoints administrativos usam Basic Auth
                - Webhooks são validados por origem
                
                ## Fluxo Principal
                1. Chatwoot envia webhook para `/webhook/chatwoot`
                2. Pipeline processa mensagem (debounce, lock, agent)
                3. Resposta é enviada de volta ao Chatwoot
                """.trimIndent(),
            ).version(version)
            .contact(
                Contact()
                    .name("Koalla Team")
                    .email("dev@koalla.ai")
                    .url("https://koalla.ai"),
            ).license(
                License()
                    .name("Proprietary")
                    .url("https://koalla.ai/terms"),
            )
}
