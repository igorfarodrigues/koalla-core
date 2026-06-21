package ai.koalla.core.controller

import ai.koalla.core.dto.UserDeactivateResponse
import ai.koalla.core.dto.UserResponse
import ai.koalla.core.mapper.toResponse
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

/**
 * User management endpoints.
 * Domain exceptions (UserNotFoundException) are handled by GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/users")
@Tag(name = "Usuários", description = "Gerenciamento de usuários do Koalla")
class UserController(
    private val userService: UserService
) {

    @GetMapping("/{waId}")
    @Operation(summary = "Buscar usuário por WhatsApp ID")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Usuário encontrado",
            content = [Content(schema = Schema(implementation = UserResponse::class))]),
        ApiResponse(responseCode = "404", description = "Usuário não encontrado")
    ])
    fun getUserByWaId(
        @Parameter(description = "Número WhatsApp no formato internacional")
        @PathVariable waId: String
    ): ResponseEntity<UserResponse> {
        val user = userService.findByWaId(waId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(user.toResponse())
    }

    @PatchMapping("/{userId}/deactivate")
    @Operation(summary = "Desativar usuário")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Usuário desativado",
            content = [Content(schema = Schema(implementation = UserDeactivateResponse::class))]),
        ApiResponse(responseCode = "404", description = "Usuário não encontrado")
    ])
    fun deactivateUser(
        @Parameter(description = "UUID do usuário")
        @PathVariable userId: UUID
    ): ResponseEntity<UserDeactivateResponse> {
        // UserNotFoundException is caught by GlobalExceptionHandler → 404
        val user = userService.deactivate(userId)
        return ResponseEntity.ok(
            UserDeactivateResponse(id = user.id, isActive = false, message = "User deactivated successfully")
        )
    }

    @PatchMapping("/{userId}/activate")
    @Operation(summary = "Reativar usuário")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Usuário reativado",
            content = [Content(schema = Schema(implementation = UserResponse::class))]),
        ApiResponse(responseCode = "404", description = "Usuário não encontrado")
    ])
    fun activateUser(
        @Parameter(description = "UUID do usuário")
        @PathVariable userId: UUID
    ): ResponseEntity<UserResponse> {
        // UserNotFoundException is caught by GlobalExceptionHandler → 404
        val user = userService.activate(userId)
        return ResponseEntity.ok(user.toResponse())
    }
}
