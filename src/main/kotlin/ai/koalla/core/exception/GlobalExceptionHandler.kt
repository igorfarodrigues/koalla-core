package ai.koalla.core.exception

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

data class ErrorResponse(val error: String)
data class ValidationErrorResponse(val message: String, val errors: Map<String, String>)

/**
 * Centralised exception → HTTP mapping.
 * Controllers no longer need try/catch blocks for domain exceptions.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(UserNotFoundException::class, ResourceNotFoundException::class)
    fun handleNotFound(e: KoallaException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(e.message ?: "Not found"))

    @ExceptionHandler(UserInactiveException::class)
    fun handleInactive(e: UserInactiveException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse(e.message ?: "Account inactive"))

    @ExceptionHandler(SubscriptionNotFoundException::class)
    fun handleSubscriptionNotFound(e: SubscriptionNotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(e.message ?: "No active subscription"))

    @ExceptionHandler(BillingException::class)
    fun handleBilling(e: BillingException): ResponseEntity<ErrorResponse> {
        logger.error("Billing error: ${e.message}", e)
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(ErrorResponse(e.message ?: "Billing error"))
    }

    @ExceptionHandler(InvalidPlanException::class)
    fun handleInvalidPlan(e: InvalidPlanException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(e.message ?: "Invalid plan"))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<ValidationErrorResponse> {
        val errors = e.bindingResult.fieldErrors.associate { fe: FieldError ->
            fe.field to (fe.defaultMessage ?: "Invalid value")
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ValidationErrorResponse(message = "Validation failed", errors = errors))
    }
}
