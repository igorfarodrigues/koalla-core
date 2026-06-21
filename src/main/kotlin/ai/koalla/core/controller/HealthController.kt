package ai.koalla.core.controller

import ai.koalla.core.config.KoallaProperties
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "Health", description = "Endpoints de monitoramento e status")
class HealthController(
    private val props: KoallaProperties
) {
    @GetMapping("/health")
    @Operation(
        summary = "Health check",
        description = "Retorna o status da aplicação e versão atual"
    )
    fun health(): Map<String, String> {
        return mapOf(
            "status" to "ok",
            "version" to props.version
        )
    }
}
