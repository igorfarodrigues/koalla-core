"""Audio transcription — mirrors the n8n Download áudio → Convert → Transcrever flow."""
import io
import tempfile
import os
from pydub import AudioSegment
from openai import AsyncOpenAI
from legacy.app.config import get_settings

settings = get_settings()
_openai = AsyncOpenAI(api_key=settings.OPENAI_API_KEY)


async def transcribe(audio_bytes: bytes) -> str:
    """
    Convert raw audio bytes to mp3 (handles ogg/opus from WhatsApp)
    and transcribe via OpenAI Whisper.
    Returns the transcribed text, or empty string on failure.
    """
    try:
        # Convert to mp3 using pydub (requires ffmpeg in PATH)
        audio = AudioSegment.from_file(io.BytesIO(audio_bytes))
        mp3_buf = io.BytesIO()
        audio.export(mp3_buf, format="mp3")
        mp3_buf.seek(0)

        response = await _openai.audio.transcriptions.create(
            model="whisper-1",
            file=("audio.mp3", mp3_buf, "audio/mpeg"),
            language="pt",
        )
        return response.text or ""
    except Exception as e:
        # Non-fatal: return placeholder so the queue entry still exists
        print(f"[audio_service] transcription failed: {e}")
        return "<mensagem de áudio não audível>"
