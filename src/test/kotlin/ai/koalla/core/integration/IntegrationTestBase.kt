package ai.koalla.core.integration

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

/**
 * Base class for integration tests.
 *
 * Provides:
 * - Full Spring context with H2 database
 * - WireMock server for external API mocking (Chatwoot, Asaas)
 * - Active profile: test
 *
 * Usage:
 * ```kotlin
 * @Tag("integration")
 * class MyIntegrationTest : IntegrationTestBase() {
 *     @Test
 *     fun `my test`() { ... }
 * }
 * ```
 *
 * Run:
 * ```bash
 * ./gradlew integrationTest
 * ```
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
abstract class IntegrationTestBase {
    companion object {
        @JvmStatic
        protected lateinit var wireMockServer: WireMockServer

        @JvmStatic
        @BeforeAll
        fun setupWireMock() {
            wireMockServer = WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort())
            wireMockServer.start()
        }

        @JvmStatic
        @AfterAll
        fun tearDownWireMock() {
            if (::wireMockServer.isInitialized && wireMockServer.isRunning) {
                wireMockServer.stop()
            }
        }

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("koalla.chatwoot.base-url") { "http://localhost:${wireMockServer.port()}" }
            registry.add("koalla.asaas.base-url") { "http://localhost:${wireMockServer.port()}" }
        }
    }

    /**
     * Reset WireMock state between tests.
     * Call in @BeforeEach if needed.
     */
    protected fun resetWireMock() {
        wireMockServer.resetAll()
    }
}
