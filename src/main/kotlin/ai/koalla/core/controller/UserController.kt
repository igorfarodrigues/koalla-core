package ai.koalla.core.controller

import ai.koalla.core.dto.UserDeactivateResponse
import ai.koalla.core.dto.UserResponse
import ai.koalla.core.service.UserService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/users")
class UserController(
    private val userService: UserService
) {

    @GetMapping("/{waId}")
    fun getUserByWaId(
        @PathVariable waId: String
    ): ResponseEntity<UserResponse> {
        val user = userService.findByWaId(waId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(userService.toResponse(user))
    }

    @PatchMapping("/{userId}/deactivate")
    fun deactivateUser(
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
    fun activateUser(
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

