package ai.koalla.core.controller

import ai.koalla.core.config.KoallaProperties
import ai.koalla.core.dto.SignupRequest
import ai.koalla.core.dto.SignupResponse
import ai.koalla.core.entity.Auth
import ai.koalla.core.entity.User
import ai.koalla.core.repository.AuthRepository
import ai.koalla.core.repository.UserRepository
import ai.koalla.core.service.BillingService
import jakarta.validation.Valid
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Auth endpoints:
 *   POST /auth/signup   — registration + trial start (with card)
 *   GET  /auth/status/{wa_id} — polling for activation by frontend
 */
@RestController
@RequestMapping("/auth")
class AuthController(
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    private val billingService: BillingService,
    private val props: KoallaProperties
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @PostMapping("/signup")
    @Transactional
    fun signup(
        @Valid @RequestBody body: SignupRequest
    ): ResponseEntity<Any> {
        val waNumber = props.koallaWaNumber
        val waLink = "https://wa.me/$waNumber?text=" +
            URLEncoder.encode("Oi Koalla! Acabei de me cadastrar 🐨", StandardCharsets.UTF_8)

        // Normalize phone number
        val normalizedPhone = normalizePhone(body.waId)

        // Check for duplicate
        val existing = userRepository.findByWaId(normalizedPhone)

        val user: User
        if (existing != null) {
            if (existing.isActive) {
                return ResponseEntity.ok(mapOf(
                    "success" to false,
                    "detail" to "already_registered",
                    "waLink" to "https://wa.me/$waNumber?text=Oi+Koalla!"
                ))
            }
            // User exists but was inactive — try to reactivate with new card
            user = existing
        } else {
            // Create new user (inactive until card is validated)
            user = User(
                waId = normalizedPhone,
                fullName = body.fullName,
                email = body.email,
                planType = body.plan.uppercase(),
                isActive = false
            )
            userRepository.save(user)

            val auth = Auth(userId = user.id!!)
            authRepository.save(auth)
        }

        // Start trial via Asaas
        return try {
            val billing = runBlocking {
                billingService.startTrial(
                    user = user,
                    plan = body.plan,
                    cardData = body.card,
                    cardHolderInfo = body.cardHolderInfo
                )
            }

            ResponseEntity.ok(SignupResponse(
                subscriptionId = billing.subscriptionId,
                trialEndDate = billing.trialEndDate,
                plan = billing.plan,
                waLink = waLink
            ))
        } catch (e: Exception) {
            logger.error("Signup failed: ${e.message}", e)
            ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(mapOf("error" to (e.message ?: "Failed to process payment")))
        }
    }

    @GetMapping("/status/{waId}")
    fun getSignupStatus(
        @PathVariable waId: String
    ): ResponseEntity<Any> {
        val normalizedPhone = normalizePhone(waId)
        val user = userRepository.findByWaId(normalizedPhone)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(mapOf("error" to "User not found"))

        return ResponseEntity.ok(mapOf(
            "waId" to user.waId,
            "isActive" to user.isActive,
            "planType" to user.planType
        ))
    }

    /**
     * Normalize phone number to include country code.
     */
    private fun normalizePhone(phone: String): String {
        var digits = phone.replace(Regex("\\D"), "")
        if (!digits.startsWith("55")) {
            digits = "55$digits"
        }
        return digits
    }
}

