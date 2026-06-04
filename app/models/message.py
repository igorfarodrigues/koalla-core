import uuid
from datetime import datetime, timezone
from sqlalchemy import String, DateTime, ForeignKey, JSON, Text
from sqlalchemy.orm import Mapped, mapped_column
from app.database import Base


def utcnow():
    return datetime.now(timezone.utc)


class WpMessageHistory(Base):
    __tablename__ = "wp_message_history"
    __table_args__ = {"schema": "koalla"}

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    user_id: Mapped[uuid.UUID | None] = mapped_column(
        ForeignKey("koalla.users.id", ondelete="CASCADE")
    )
    direction: Mapped[str] = mapped_column(String(10), nullable=False)  # INBOUND | OUTBOUND
    metadata_: Mapped[dict] = mapped_column("metadata", JSON, default=dict)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=utcnow
    )


# ── Internal tables (replaces n8n postgres tables) ──────────────────────────

class MessageQueue(Base):
    """Replaces n8n_fila_mensagens — debounce buffer for incoming messages."""
    __tablename__ = "message_queue"
    __table_args__ = {"schema": "koalla"}

    id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
    wa_id: Mapped[str] = mapped_column(String(20), nullable=False, index=True)
    message_id: Mapped[str] = mapped_column(String(100), nullable=False)
    message: Mapped[str] = mapped_column(Text, nullable=False)
    timestamp: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)


class ChatHistory(Base):
    """LangChain-compatible chat history. Replaces n8n_historico_mensagens."""
    __tablename__ = "chat_history"
    __table_args__ = {"schema": "koalla"}

    id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
    session_id: Mapped[str] = mapped_column(String(50), nullable=False, index=True)
    message: Mapped[dict] = mapped_column(JSON, nullable=False)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=utcnow
    )


class ConversationStatus(Base):
    """Processing lock per conversation. Replaces n8n_status_atendimento."""
    __tablename__ = "conversation_status"
    __table_args__ = {"schema": "koalla"}

    session_id: Mapped[str] = mapped_column(String(50), primary_key=True)
    lock_conversa: Mapped[bool] = mapped_column(default=False)
    aguardando_followup: Mapped[bool] = mapped_column(default=False)
    numero_followup: Mapped[int] = mapped_column(default=0)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=utcnow, onupdate=utcnow
    )
