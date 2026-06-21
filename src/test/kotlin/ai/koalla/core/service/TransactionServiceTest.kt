package ai.koalla.core.service

import ai.koalla.core.domain.EntityContext
import ai.koalla.core.domain.MovementType
import ai.koalla.core.dto.TransactionCreateRequest
import ai.koalla.core.entity.CategoryEntity
import ai.koalla.core.entity.TransactionEntity
import ai.koalla.core.repository.CategoryRepository
import ai.koalla.core.repository.TransactionRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageRequest
import java.time.OffsetDateTime
import java.util.Optional
import java.util.UUID

class TransactionServiceTest {
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var transactionService: TransactionService

    @BeforeEach
    fun setup() {
        transactionRepository = mockk()
        categoryRepository = mockk()
        transactionService = TransactionService(transactionRepository, categoryRepository)
    }

    @Nested
    inner class Create {
        @Test
        fun `should create transaction from request`() {
            // Given
            val userId = UUID.randomUUID()
            val now = OffsetDateTime.now()
            val request =
                TransactionCreateRequest(
                    userId = userId,
                    description = "Uber",
                    amount = 2500L,
                    movement = MovementType.CASH_OUT,
                    categoryId = 5,
                    entityType = EntityContext.PF,
                    source = "whatsapp",
                    occurredAt = now,
                )
            val slot = slot<TransactionEntity>()
            every { transactionRepository.save(capture(slot)) } answers {
                slot.captured.apply { id = UUID.randomUUID() }
            }

            // When
            val result = transactionService.create(request)

            // Then
            result.shouldNotBeNull()
            result.userId shouldBeEqualTo userId
            result.description shouldBeEqualTo "Uber"
            result.amount shouldBeEqualTo 2500L
            result.movement shouldBeEqualTo MovementType.CASH_OUT
            result.categoryId shouldBeEqualTo 5
            result.entityType shouldBeEqualTo EntityContext.PF
            result.source shouldBeEqualTo "whatsapp"
            result.occurredAt shouldBeEqualTo now
        }

        @Test
        fun `should use current time when occurredAt is null`() {
            // Given
            val userId = UUID.randomUUID()
            val request =
                TransactionCreateRequest(
                    userId = userId,
                    amount = 1000L,
                    movement = MovementType.CASH_IN,
                    occurredAt = null,
                )
            val slot = slot<TransactionEntity>()
            every { transactionRepository.save(capture(slot)) } answers {
                slot.captured.apply { id = UUID.randomUUID() }
            }

            // When
            val result = transactionService.create(request)

            // Then
            result.occurredAt.shouldNotBeNull()
        }
    }

    @Nested
    inner class ListByUser {
        @Test
        fun `should return transactions ordered by occurredAt desc`() {
            // Given
            val userId = UUID.randomUUID()
            val entities =
                listOf(
                    createTransactionEntity(userId = userId, amount = 1000L),
                    createTransactionEntity(userId = userId, amount = 2000L),
                )
            every {
                transactionRepository.findByUserIdOrderByOccurredAtDesc(userId, PageRequest.of(0, 50))
            } returns entities

            // When
            val result = transactionService.listByUser(userId)

            // Then
            result shouldHaveSize 2
            result[0].amount shouldBeEqualTo 1000L
            result[1].amount shouldBeEqualTo 2000L
        }

        @Test
        fun `should respect custom limit`() {
            // Given
            val userId = UUID.randomUUID()
            val limit = 10
            every {
                transactionRepository.findByUserIdOrderByOccurredAtDesc(userId, PageRequest.of(0, limit))
            } returns emptyList()

            // When
            transactionService.listByUser(userId, limit)

            // Then
            verify { transactionRepository.findByUserIdOrderByOccurredAtDesc(userId, PageRequest.of(0, 10)) }
        }
    }

    @Nested
    inner class FindById {
        @Test
        fun `should return transaction when found`() {
            // Given
            val id = UUID.randomUUID()
            val entity = createTransactionEntity(id = id)
            every { transactionRepository.findById(id) } returns Optional.of(entity)

            // When
            val result = transactionService.findById(id)

            // Then
            result.shouldNotBeNull()
            result.id shouldBeEqualTo id
        }

        @Test
        fun `should return null when not found`() {
            // Given
            val id = UUID.randomUUID()
            every { transactionRepository.findById(id) } returns Optional.empty()

            // When
            val result = transactionService.findById(id)

            // Then
            result shouldBeEqualTo null
        }
    }

    @Nested
    inner class Delete {
        @Test
        fun `should delete transaction and return true`() {
            // Given
            val id = UUID.randomUUID()
            val entity = createTransactionEntity(id = id)
            every { transactionRepository.findById(id) } returns Optional.of(entity)
            every { transactionRepository.delete(entity) } returns Unit

            // When
            val result = transactionService.delete(id)

            // Then
            result shouldBeEqualTo true
            verify { transactionRepository.delete(entity) }
        }

        @Test
        fun `should return false when transaction not found`() {
            // Given
            val id = UUID.randomUUID()
            every { transactionRepository.findById(id) } returns Optional.empty()

            // When
            val result = transactionService.delete(id)

            // Then
            result shouldBeEqualTo false
            verify(exactly = 0) { transactionRepository.delete(any()) }
        }
    }

    @Nested
    inner class GetMonthlySummary {
        @Test
        fun `should calculate monthly summary correctly`() {
            // Given
            val userId = UUID.randomUUID()
            val year = 2026
            val month = 6

            val transactions =
                listOf(
                    createTransactionEntity(userId = userId, amount = 100000L, movement = MovementType.CASH_IN, categoryId = 1),
                    createTransactionEntity(userId = userId, amount = 25000L, movement = MovementType.CASH_OUT, categoryId = 2),
                    createTransactionEntity(userId = userId, amount = 15000L, movement = MovementType.CASH_OUT, categoryId = 2),
                )

            val categories =
                listOf(
                    CategoryEntity(name = "Receita").apply { id = 1 },
                    CategoryEntity(name = "Alimentação").apply { id = 2 },
                )

            every { transactionRepository.sumCashInByUserIdAndPeriod(userId, any(), any()) } returns 100000L
            every { transactionRepository.sumCashOutByUserIdAndPeriod(userId, any(), any()) } returns 40000L
            every { transactionRepository.findByUserIdAndPeriod(userId, any(), any()) } returns transactions
            every { categoryRepository.findAllById(any<Iterable<Int>>()) } returns categories

            // When
            val result = transactionService.getMonthlySummary(userId, year, month)

            // Then
            result.totalCashIn shouldBeEqualTo 100000L
            result.totalCashOut shouldBeEqualTo 40000L
            result.balance shouldBeEqualTo 60000L
            result.byCategory["Alimentação"] shouldBeEqualTo 40000L
        }

        @Test
        fun `should handle empty transactions`() {
            // Given
            val userId = UUID.randomUUID()
            every { transactionRepository.sumCashInByUserIdAndPeriod(userId, any(), any()) } returns 0L
            every { transactionRepository.sumCashOutByUserIdAndPeriod(userId, any(), any()) } returns 0L
            every { transactionRepository.findByUserIdAndPeriod(userId, any(), any()) } returns emptyList()

            // When
            val result = transactionService.getMonthlySummary(userId, 2026, 6)

            // Then
            result.totalCashIn shouldBeEqualTo 0L
            result.totalCashOut shouldBeEqualTo 0L
            result.balance shouldBeEqualTo 0L
            result.byCategory shouldBeEqualTo emptyMap()
        }
    }

    @Nested
    inner class RegisterTransaction {
        @Test
        fun `should register transaction with category lookup`() {
            // Given
            val userId = UUID.randomUUID()
            val categories =
                listOf(
                    CategoryEntity(name = "Alimentação").apply { id = 1 },
                    CategoryEntity(name = "Transporte").apply { id = 2 },
                )
            every { categoryRepository.findByUserIdIsNull() } returns categories

            val slot = slot<TransactionEntity>()
            every { transactionRepository.save(capture(slot)) } answers {
                slot.captured.apply { id = UUID.randomUUID() }
            }

            // When
            val result =
                transactionService.registerTransaction(
                    userId = userId,
                    description = "Almoço",
                    amount = 3500L,
                    movement = MovementType.CASH_OUT,
                    categoryName = "alimentação",
                )

            // Then
            result.shouldNotBeNull()
            result.description shouldBeEqualTo "Almoço"
            result.amount shouldBeEqualTo 3500L
            result.categoryId shouldBeEqualTo 1
        }

        @Test
        fun `should register transaction with null category when not found`() {
            // Given
            val userId = UUID.randomUUID()
            every { categoryRepository.findByUserIdIsNull() } returns emptyList()

            val slot = slot<TransactionEntity>()
            every { transactionRepository.save(capture(slot)) } answers {
                slot.captured.apply { id = UUID.randomUUID() }
            }

            // When
            val result =
                transactionService.registerTransaction(
                    userId = userId,
                    description = "Gasto",
                    amount = 1000L,
                    movement = MovementType.CASH_OUT,
                    categoryName = "Unknown",
                )

            // Then
            result.categoryId shouldBeEqualTo null
        }

        @Test
        fun `should register transaction without category`() {
            // Given
            val userId = UUID.randomUUID()
            val slot = slot<TransactionEntity>()
            every { transactionRepository.save(capture(slot)) } answers {
                slot.captured.apply { id = UUID.randomUUID() }
            }

            // When
            val result =
                transactionService.registerTransaction(
                    userId = userId,
                    description = "Receita",
                    amount = 50000L,
                    movement = MovementType.CASH_IN,
                )

            // Then
            result.categoryId shouldBeEqualTo null
            verify(exactly = 0) { categoryRepository.findByUserIdIsNull() }
        }
    }

    private fun createTransactionEntity(
        id: UUID = UUID.randomUUID(),
        userId: UUID = UUID.randomUUID(),
        amount: Long = 1000L,
        movement: MovementType = MovementType.CASH_OUT,
        categoryId: Int? = null,
    ): TransactionEntity =
        TransactionEntity(
            userId = userId,
            amount = amount,
            movement = movement,
            categoryId = categoryId,
        ).apply {
            this.id = id
            this.createdAt = OffsetDateTime.now()
            this.updatedAt = OffsetDateTime.now()
        }
}
