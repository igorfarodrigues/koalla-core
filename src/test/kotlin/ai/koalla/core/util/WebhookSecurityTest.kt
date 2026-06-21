package ai.koalla.core.util

import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class WebhookSecurityTest {
    @Nested
    inner class SecureCompareTests {
        @Test
        fun `should return true for identical strings`() {
            secureCompare("secret123", "secret123").shouldBeTrue()
        }

        @Test
        fun `should return false for different strings of same length`() {
            secureCompare("secret123", "secret456").shouldBeFalse()
        }

        @Test
        fun `should return false for strings of different length`() {
            secureCompare("short", "longer").shouldBeFalse()
        }

        @Test
        fun `should return false when first string is longer`() {
            secureCompare("longer", "short").shouldBeFalse()
        }

        @Test
        fun `should return true for empty strings`() {
            secureCompare("", "").shouldBeTrue()
        }

        @Test
        fun `should return false for empty vs non-empty`() {
            secureCompare("", "nonempty").shouldBeFalse()
            secureCompare("nonempty", "").shouldBeFalse()
        }

        @Test
        fun `should handle special characters`() {
            secureCompare("!@#$%^&*()", "!@#$%^&*()").shouldBeTrue()
            secureCompare("!@#$%^&*()", "!@#$%^&*()_").shouldBeFalse()
        }

        @Test
        fun `should handle unicode characters`() {
            secureCompare("こんにちは", "こんにちは").shouldBeTrue()
            secureCompare("こんにちは", "こんにちわ").shouldBeFalse()
        }

        @Test
        fun `should be case sensitive`() {
            secureCompare("Secret", "secret").shouldBeFalse()
            secureCompare("SECRET", "secret").shouldBeFalse()
        }

        @Test
        fun `should handle whitespace correctly`() {
            secureCompare("with space", "with space").shouldBeTrue()
            secureCompare("with space", "withspace").shouldBeFalse()
            secureCompare("  ", "  ").shouldBeTrue()
        }

        @Test
        fun `should handle typical webhook signatures`() {
            val signature = "sha256=abc123def456ghi789"
            secureCompare(signature, signature).shouldBeTrue()
            secureCompare(signature, "sha256=abc123def456ghi788").shouldBeFalse()
        }
    }
}
