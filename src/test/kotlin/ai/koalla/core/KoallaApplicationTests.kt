package ai.koalla.core

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
@Import(TestConfig::class)
class KoallaApplicationTests {

    @Test
    fun contextLoads() {
        // Basic smoke test to verify Spring context loads
    }
}

