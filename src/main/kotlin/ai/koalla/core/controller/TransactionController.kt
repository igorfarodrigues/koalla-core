package ai.koalla.core.controller

import ai.koalla.core.dto.TransactionCreateRequest
import ai.koalla.core.dto.TransactionResponse
import ai.koalla.core.mapper.toResponse
import ai.koalla.core.service.TransactionService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/transactions")
@Tag(name = "Transações", description = "Gerenciamento de transações financeiras")
class TransactionController(
    private val transactionService: TransactionService
) {

    @PostMapping
    @Operation(summary = "Criar transação")
    @ApiResponses(value = [
        ApiResponse(responseCode = "201", description = "Transação criada",
            content = [Content(schema = Schema(implementation = TransactionResponse::class))]),
        ApiResponse(responseCode = "400", description = "Dados inválidos")
    ])
    fun createTransaction(
        @Valid @RequestBody request: TransactionCreateRequest
    ): ResponseEntity<TransactionResponse> {
        val transaction = transactionService.create(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(transaction.toResponse())
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Listar transações do usuário")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Lista de transações",
            content = [Content(array = ArraySchema(schema = Schema(implementation = TransactionResponse::class)))])
    ])
    fun listUserTransactions(
        @Parameter(description = "UUID do usuário")
        @PathVariable userId: UUID,
        @Parameter(description = "Quantidade máxima")
        @RequestParam(defaultValue = "50") limit: Int
    ): ResponseEntity<List<TransactionResponse>> {
        val transactions = transactionService.listByUser(userId, limit)
        return ResponseEntity.ok(transactions.map { it.toResponse() })
    }

    @DeleteMapping("/{transactionId}")
    @Operation(summary = "Excluir transação")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Transação excluída"),
        ApiResponse(responseCode = "404", description = "Transação não encontrada")
    ])
    fun deleteTransaction(
        @Parameter(description = "UUID da transação")
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
