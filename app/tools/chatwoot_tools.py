"""Ferramentas de integração com Chatwoot utilizadas pelo Koalla."""
from langchain_core.tools import tool
from typing import Annotated
from app.services import chatwoot_client


# These tools receive context via closure — set before agent invocation
_ctx: dict = {}


def set_context(ctx: dict) -> None:
    """Inject conversation context (account_id, conversation_id, etc.)."""
    _ctx.update(ctx)


@tool
async def send_text(
    content: Annotated[str, "The text message to send to the user."]
) -> str:
    """
    Send a text message to the user via Chatwoot.
    Use for links, phone numbers, or formatted info that doesn't work well as audio.
    NEVER include the sent data again in your output after calling this tool.
    """
    await chatwoot_client.send_message(
        _ctx["account_id"], _ctx["conversation_id"], content
    )
    return "Message sent."


@tool
async def react_to_message(
    emoji: Annotated[str, "The emoji to react with. Allowed: 😀 ❤️ 👍 👀 ✅"]
) -> str:
    """
    Send an emoji reaction to the user's last message.
    Use max 3 times per conversation.
    NEVER use this tool multiple times in a row.
    """
    await chatwoot_client.send_reaction(
        _ctx["account_id"],
        _ctx["conversation_id"],
        int(_ctx["message_id"]),
        emoji,
    )
    return "Reaction sent."


@tool
async def set_response_preference(
    preference: Annotated[str, "User preference: 'audio' or 'texto'"]
) -> str:
    """
    Save the user's preference for receiving responses as audio or text.
    Only use when the user explicitly says they want only audio or only text.
    """
    current_attrs = _ctx.get("contact_custom_attributes", {})
    await chatwoot_client.update_contact_attributes(
        _ctx["account_id"],
        _ctx["contact_id"],
        {**current_attrs, "preferencia_audio_texto": preference},
    )
    return f"Preference set to '{preference}'."


@tool
async def send_cancellation_alert(
    message: Annotated[str, "Alert message with name, date/time, reason and notes."]
) -> str:
    """Send a cancellation alert to the manager's conversation."""
    alert_conv_id = _ctx.get("alert_conversation_id")
    if not alert_conv_id:
        return "Alert conversation not configured."
    await chatwoot_client.send_message(_ctx["account_id"], int(alert_conv_id), message)
    return "Alert sent."
