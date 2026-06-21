package ai.koalla.core.controller

import ai.koalla.core.dto.TransactionCreateRequest
import ai.koalla.core.dto.TransactionResponse
import ai.koalla.core.service.TransactionService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/transactions")
class TransactionController(
    private val transactionService: TransactionService
) {

    @PostMapping
    fun createTransaction(
        @Valid @RequestBody request: TransactionCreateRequest
    ): ResponseEntity<TransactionResponse> {
        val transaction = transactionService.create(request)
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(transactionService.toResponse(transaction))
    }

    @GetMapping("/user/{userId}")
    fun listUserTransactions(
        @PathVariable userId: UUID,
        @RequestParam(defaultValue = "50") limit: Int
    ): ResponseEntity<List<TransactionResponse>> {
        val transactions = transactionService.listByUser(userId, limit)
        return ResponseEntity.ok(transactions.map { transactionService.toResponse(it) })
    }

    @DeleteMapping("/{transactionId}")
    fun deleteTransaction(
        @PathVariable transactionId: UUID
    ): ResponseEntity<Map<String, String>> {
        val deleted = transactionService.delete(transactionId)
        return if (deleted) {
            ResponseEntity.ok(mapOf("deleted" to transactionId.toString()))
        } else {
            ResponseEntity.notFound().build()
        }
    }
}

