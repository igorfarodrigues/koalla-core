package ai.koalla.core.controller

import ai.koalla.core.dto.UserDeactivateResponse
import ai.koalla.core.dto.UserResponse
import ai.koalla.core.service.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/users")
@Tag(name = "Usuários", description = "Gerenciamento de usuários do Koalla")
class UserController(
    private val userService: UserService
) {

    @GetMapping("/{waId}")
    @Operation(
        summary = "Buscar usuário por WhatsApp ID",
        description = "Retorna os dados do usuário associado ao número de WhatsApp"
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Usuário encontrado",
            content = [Content(schema = Schema(implementation = UserResponse::class))]),
        ApiResponse(responseCode = "404", description = "Usuário não encontrado")
    ])
    fun getUserByWaId(
        @Parameter(description = "Número WhatsApp no formato internacional (ex: +5531999999999)")
        @PathVariable waId: String
    ): ResponseEntity<UserResponse> {
        val user = userService.findByWaId(waId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(userService.toResponse(user))
    }

    @PatchMapping("/{userId}/deactivate")
    @Operation(
        summary = "Desativar usuário",
        description = "Desativa a conta do usuário, impedindo-o de usar o Koalla"
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Usuário desativado",
            content = [Content(schema = Schema(implementation = UserDeactivateResponse::class))]),
        ApiResponse(responseCode = "404", description = "Usuário não encontrado")
    ])
    fun deactivateUser(
        @Parameter(description = "UUID do usuário")
        @PathVariable userId: UUID
    ): ResponseEntity<UserDeactivateResponse> {
        return try {
            val response = userService.deactivate(userId)
            ResponseEntity.ok(response)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.notFound().build()
        }
    }

    @PatchMapping("/{userId}/activate")
    @Operation(
        summary = "Reativar usuário",
        description = "Reativa a conta de um usuário previamente desativado"
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Usuário reativado",
            content = [Content(schema = Schema(implementation = UserResponse::class))]),
        ApiResponse(responseCode = "404", description = "Usuário não encontrado")
    ])
    fun activateUser(
        @Parameter(description = "UUID do usuário")
        @PathVariable userId: UUID
    ): ResponseEntity<UserResponse> {
        return try {
            val user = userService.activate(userId)
            ResponseEntity.ok(userService.toResponse(user))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.notFound().build()
        }
    }
}
