package ai.koalla.core.exception

import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeInstanceOf
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class KoallaExceptionsTest {

    @Nested
    inner class UserNotFoundExceptionTests {
        @Test
        fun `should create exception with identifier`() {
            val exception = UserNotFoundException("user-123")

            exception.message shouldBeEqualTo "User not found: user-123"
            exception shouldBeInstanceOf KoallaException::class
        }

        @Test
        fun `should work with UUID identifier`() {
            val uuid = "550e8400-e29b-41d4-a716-446655440000"
            val exception = UserNotFoundException(uuid)

            exception.message shouldBeEqualTo "User not found: $uuid"
        }

        @Test
        fun `should work with phone number identifier`() {
            val phone = "5511999999999"
            val exception = UserNotFoundException(phone)

            exception.message shouldBeEqualTo "User not found: $phone"
        }
    }

    @Nested
    inner class UserInactiveExceptionTests {
        @Test
        fun `should create exception with waId`() {
            val exception = UserInactiveException("5511999999999")

            exception.message shouldBeEqualTo "User inactive: 5511999999999"
            exception shouldBeInstanceOf KoallaException::class
        }
    }

    @Nested
    inner class SubscriptionNotFoundExceptionTests {
        @Test
        fun `should create exception with userId`() {
            val userId = "550e8400-e29b-41d4-a716-446655440000"
            val exception = SubscriptionNotFoundException(userId)

            exception.message shouldBeEqualTo "No active subscription for user: $userId"
            exception shouldBeInstanceOf KoallaException::class
        }
    }

    @Nested
    inner class BillingExceptionTests {
        @Test
        fun `should create exception with message only`() {
            val exception = BillingException("Payment failed")

            exception.message shouldBeEqualTo "Payment failed"
            exception.cause.shouldBeNull()
            exception shouldBeInstanceOf KoallaException::class
        }

        @Test
        fun `should create exception with message and cause`() {
            val cause = RuntimeException("Network error")
            val exception = BillingException("Payment failed", cause)

            exception.message shouldBeEqualTo "Payment failed"
            exception.cause.shouldNotBeNull()
            exception.cause shouldBeEqualTo cause
        }
    }

    @Nested
    inner class InvalidPlanExceptionTests {
        @Test
        fun `should create exception with plan name`() {
            val exception = InvalidPlanException("INVALID")

            exception.message shouldBeEqualTo "Invalid plan: INVALID. Valid values: STARTER, PRO, BUSINESS"
            exception shouldBeInstanceOf KoallaException::class
        }
    }

    @Nested
    inner class ResourceNotFoundExceptionTests {
        @Test
        fun `should create exception with custom message`() {
            val exception = ResourceNotFoundException("Transaction not found")

            exception.message shouldBeEqualTo "Transaction not found"
            exception shouldBeInstanceOf KoallaException::class
        }
    }

    @Nested
    inner class HierarchyTests {
        @Test
        fun `all exceptions should extend KoallaException`() {
            val exceptions = listOf(
                UserNotFoundException("test"),
                UserInactiveException("test"),
                SubscriptionNotFoundException("test"),
                BillingException("test"),
                InvalidPlanException("test"),
                ResourceNotFoundException("test")
            )

            exceptions.forEach { exception ->
                exception shouldBeInstanceOf KoallaException::class
                exception shouldBeInstanceOf RuntimeException::class
            }
        }

        @Test
        fun `KoallaException should be catchable as parent type`() {
            val exception: KoallaException = UserNotFoundException("test")

            val caught = try {
                throw exception
            } catch (e: KoallaException) {
                e
            }

            caught shouldBeInstanceOf UserNotFoundException::class
        }
    }
}

