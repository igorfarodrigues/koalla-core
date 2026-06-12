from pydantic import BaseModel, Field
from typing import Any

# ============================================================================
# IMPORTANTE
# ============================================================================
# Utilizamos Field(default_factory=...) para evitar que listas, dicionários
# e objetos sejam compartilhados entre instâncias do Pydantic.
#
# Exemplo ruim:
#   labels: list[str] = []
#
# Exemplo correto:
#   labels: list[str] = Field(default_factory=list)
#
# Isso evita bugs difíceis de rastrear quando vários webhooks são processados
# simultaneamente.
# ============================================================================


class ChatwootSender(BaseModel):
    id: int | None = None
    name: str | None = None
    phone_number: str | None = None
    email: str | None = None

    # Evita compartilhamento do mesmo dicionário entre instâncias
    custom_attributes: dict[str, Any] = Field(default_factory=dict)


class ChatwootAttachment(BaseModel):
    id: int | None = None
    file_type: str | None = None
    data_url: str | None = None

    # Evita compartilhamento do mesmo dicionário
    meta: dict[str, Any] = Field(default_factory=dict)

    @property
    def is_recorded_audio(self) -> bool:
        return bool(self.meta.get("is_recorded_audio", False))


class ChatwootContactInbox(BaseModel):
    contact_id: int | None = None
    source_id: str | None = None


class ChatwootConversationMeta(BaseModel):
    # Evita reutilização da mesma instância ChatwootSender
    sender: ChatwootSender = Field(default_factory=ChatwootSender)


class ChatwootConversation(BaseModel):
    id: int | None = None

    # Evita compartilhamento de lista
    labels: list[str] = Field(default_factory=list)

    # Evita compartilhamento de dicionário
    custom_attributes: dict[str, Any] = Field(default_factory=dict)

    # Evita reutilização da mesma instância
    contact_inbox: ChatwootContactInbox = Field(
        default_factory=ChatwootContactInbox
    )

    # Evita reutilização da mesma instância
    meta: ChatwootConversationMeta = Field(
        default_factory=ChatwootConversationMeta
    )

    # Evita compartilhamento de lista
    messages: list[dict[str, Any]] = Field(default_factory=list)


class ChatwootAccount(BaseModel):
    id: int | None = None
    name: str | None = None


class ChatwootWebhookBody(BaseModel):
    id: int | None = None

    # Evita reutilização da mesma instância
    account: ChatwootAccount = Field(default_factory=ChatwootAccount)

    content: str | None = None
    message_type: str | None = None
    created_at: str | None = None

    # Evita reutilização da mesma instância
    sender: ChatwootSender = Field(default_factory=ChatwootSender)

    # Evita reutilização da mesma instância
    conversation: ChatwootConversation = Field(
        default_factory=ChatwootConversation
    )

    # Evita compartilhamento de lista
    attachments: list[ChatwootAttachment] = Field(default_factory=list)

    event: str | None = None


class ChatwootWebhookPayload(BaseModel):
    body: ChatwootWebhookBody

    @classmethod
    def from_raw(cls, data: dict) -> "ChatwootWebhookPayload":
        return cls(body=ChatwootWebhookBody(**data))