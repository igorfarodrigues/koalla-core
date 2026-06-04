from pydantic import BaseModel
from typing import Any


class ChatwootSender(BaseModel):
    id: int | None = None
    name: str | None = None
    phone_number: str | None = None
    email: str | None = None
    custom_attributes: dict[str, Any] = {}


class ChatwootAttachment(BaseModel):
    id: int | None = None
    file_type: str | None = None
    data_url: str | None = None
    meta: dict[str, Any] = {}

    @property
    def is_recorded_audio(self) -> bool:
        return bool(self.meta.get("is_recorded_audio", False))


class ChatwootContactInbox(BaseModel):
    contact_id: int | None = None
    source_id: str | None = None


class ChatwootConversationMeta(BaseModel):
    sender: ChatwootSender = ChatwootSender()


class ChatwootConversation(BaseModel):
    id: int | None = None
    labels: list[str] = []
    custom_attributes: dict[str, Any] = {}
    contact_inbox: ChatwootContactInbox = ChatwootContactInbox()
    meta: ChatwootConversationMeta = ChatwootConversationMeta()
    messages: list[dict[str, Any]] = []


class ChatwootAccount(BaseModel):
    id: int | None = None
    name: str | None = None


class ChatwootWebhookBody(BaseModel):
    id: int | None = None
    account: ChatwootAccount = ChatwootAccount()
    content: str | None = None
    message_type: str | None = None
    created_at: str | None = None
    sender: ChatwootSender = ChatwootSender()
    conversation: ChatwootConversation = ChatwootConversation()
    attachments: list[ChatwootAttachment] = []
    event: str | None = None


class ChatwootWebhookPayload(BaseModel):
    body: ChatwootWebhookBody

    @classmethod
    def from_raw(cls, data: dict) -> "ChatwootWebhookPayload":
        return cls(body=ChatwootWebhookBody(**data))
