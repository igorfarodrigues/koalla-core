package ai.koalla.core.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

/**
 * Audio transcription service using OpenAI Whisper API.
 * Handles audio conversion and transcription for WhatsApp voice messages.
 */
@Service
class AudioService(
    @Value("\${spring.ai.openai.api-key}") private val openaiApiKey: String,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val webClient =
        WebClient
            .builder()
            .baseUrl("https://api.openai.com/v1")
            .defaultHeader("Authorization", "Bearer $openaiApiKey")
            .codecs { it.defaultCodecs().maxInMemorySize(16 * 1024 * 1024) } // 16MB for audio files
            .build()

    /**
     * Transcribe audio bytes using OpenAI Whisper API.
     * Supports ogg/opus format from WhatsApp directly.
     *
     * @param audioBytes Raw audio bytes (typically ogg/opus from WhatsApp)
     * @return Transcribed text, or placeholder message on failure
     */
    suspend fun transcribe(audioBytes: ByteArray): String {
        if (audioBytes.isEmpty()) {
            logger.warn("Empty audio bytes received")
            return "<mensagem de áudio vazia>"
        }

        return try {
            logger.info("Transcribing audio: ${audioBytes.size} bytes")

            // Detect format from magic bytes
            val (filename, contentType) = detectAudioFormat(audioBytes)

            val bodyBuilder = MultipartBodyBuilder()
            bodyBuilder
                .part(
                    "file",
                    object : ByteArrayResource(audioBytes) {
                        override fun getFilename(): String = filename
                    },
                ).contentType(MediaType.parseMediaType(contentType))
            bodyBuilder.part("model", "whisper-1")
            bodyBuilder.part("language", "pt")

            val response =
                webClient
                    .post()
                    .uri("/audio/transcriptions")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                    .retrieve()
                    .awaitBody<WhisperResponse>()

            val text = response.text?.trim()
            if (text.isNullOrBlank()) {
                logger.warn("Whisper returned empty transcription")
                "<mensagem de áudio não audível>"
            } else {
                logger.info("Transcription successful: ${text.take(50)}...")
                text
            }
        } catch (e: Exception) {
            logger.error("Transcription failed: ${e.message}", e)
            "<mensagem de áudio não audível>"
        }
    }

    /**
     * Detect audio format from magic bytes.
     * Returns (filename, contentType) tuple.
     */
    private fun detectAudioFormat(bytes: ByteArray): Pair<String, String> =
        when {
            // OGG format (used by WhatsApp voice messages)
            bytes.size >= 4 && bytes.slice(0..3) == listOf<Byte>(0x4F, 0x67, 0x67, 0x53) ->
                "audio.ogg" to "audio/ogg"
            // MP3 format
            bytes.size >= 2 && (bytes[0] == 0xFF.toByte() && (bytes[1].toInt() and 0xE0) == 0xE0) ->
                "audio.mp3" to "audio/mpeg"
            // MP3 with ID3 tag
            bytes.size >= 3 && bytes.slice(0..2) == listOf<Byte>(0x49, 0x44, 0x33) ->
                "audio.mp3" to "audio/mpeg"
            // WAV format
            bytes.size >= 4 && bytes.slice(0..3) == listOf<Byte>(0x52, 0x49, 0x46, 0x46) ->
                "audio.wav" to "audio/wav"
            // M4A/AAC format
            bytes.size >= 8 && bytes.slice(4..7) == listOf<Byte>(0x66, 0x74, 0x79, 0x70) ->
                "audio.m4a" to "audio/mp4"
            // WebM format
            bytes.size >= 4 && bytes.slice(0..3) == listOf<Byte>(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte()) ->
                "audio.webm" to "audio/webm"
            // Default to ogg (most common for WhatsApp)
            else -> "audio.ogg" to "audio/ogg"
        }

    data class WhisperResponse(
        val text: String? = null,
    )
}
