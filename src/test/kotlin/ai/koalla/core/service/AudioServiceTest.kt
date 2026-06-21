package ai.koalla.core.service

import kotlinx.coroutines.runBlocking
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class AudioServiceTest {

    // Create AudioService with a dummy API key for testing (external calls will be mocked or skipped)
    private val audioService = AudioService("test-api-key")

    @Nested
    inner class TranscribeTests {
        @Test
        fun `should return empty audio message for empty bytes`() = runBlocking {
            val result = audioService.transcribe(byteArrayOf())

            result shouldBeEqualTo "<mensagem de áudio vazia>"
        }
    }

    @Nested
    inner class DetectAudioFormatTests {
        // Using reflection to test private method
        private fun detectFormat(bytes: ByteArray): Pair<String, String> {
            val method = AudioService::class.java.getDeclaredMethod("detectAudioFormat", ByteArray::class.java)
            method.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            return method.invoke(audioService, bytes) as Pair<String, String>
        }

        @Test
        fun `should detect OGG format from magic bytes`() {
            // OGG magic bytes: 0x4F 0x67 0x67 0x53 = "OggS"
            val oggBytes = byteArrayOf(0x4F, 0x67, 0x67, 0x53, 0x00, 0x00)

            val (filename, contentType) = detectFormat(oggBytes)

            filename shouldBeEqualTo "audio.ogg"
            contentType shouldBeEqualTo "audio/ogg"
        }

        @Test
        fun `should detect MP3 format from frame sync`() {
            // MP3 frame sync: 0xFF followed by byte with high 3 bits set (0xE0)
            val mp3Bytes = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00)

            val (filename, contentType) = detectFormat(mp3Bytes)

            filename shouldBeEqualTo "audio.mp3"
            contentType shouldBeEqualTo "audio/mpeg"
        }

        @Test
        fun `should detect MP3 with ID3 tag`() {
            // ID3 magic bytes: 0x49 0x44 0x33 = "ID3"
            val id3Bytes = byteArrayOf(0x49, 0x44, 0x33, 0x00, 0x00)

            val (filename, contentType) = detectFormat(id3Bytes)

            filename shouldBeEqualTo "audio.mp3"
            contentType shouldBeEqualTo "audio/mpeg"
        }

        @Test
        fun `should detect WAV format`() {
            // WAV magic bytes: 0x52 0x49 0x46 0x46 = "RIFF"
            val wavBytes = byteArrayOf(0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00)

            val (filename, contentType) = detectFormat(wavBytes)

            filename shouldBeEqualTo "audio.wav"
            contentType shouldBeEqualTo "audio/wav"
        }

        @Test
        fun `should detect M4A format from ftyp box`() {
            // M4A/AAC: bytes 4-7 are "ftyp" (0x66 0x74 0x79 0x70)
            val m4aBytes = byteArrayOf(0x00, 0x00, 0x00, 0x1C, 0x66, 0x74, 0x79, 0x70)

            val (filename, contentType) = detectFormat(m4aBytes)

            filename shouldBeEqualTo "audio.m4a"
            contentType shouldBeEqualTo "audio/mp4"
        }

        @Test
        fun `should detect WebM format`() {
            // WebM magic bytes: 0x1A 0x45 0xDF 0xA3
            val webmBytes = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte(), 0x00)

            val (filename, contentType) = detectFormat(webmBytes)

            filename shouldBeEqualTo "audio.webm"
            contentType shouldBeEqualTo "audio/webm"
        }

        @Test
        fun `should default to OGG for unknown format`() {
            // Random bytes that don't match any known format
            val unknownBytes = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04)

            val (filename, contentType) = detectFormat(unknownBytes)

            filename shouldBeEqualTo "audio.ogg"
            contentType shouldBeEqualTo "audio/ogg"
        }

        @Test
        fun `should default to OGG for very short bytes`() {
            val shortBytes = byteArrayOf(0x01)

            val (filename, contentType) = detectFormat(shortBytes)

            filename shouldBeEqualTo "audio.ogg"
            contentType shouldBeEqualTo "audio/ogg"
        }

        @Test
        fun `should default to OGG for empty bytes`() {
            val (filename, contentType) = detectFormat(byteArrayOf())

            filename shouldBeEqualTo "audio.ogg"
            contentType shouldBeEqualTo "audio/ogg"
        }
    }
}

