"""Human escalation tool — mirrors n8n subworkflow 05."""
from langchain_core.tools import tool
from typing import Annotated
from legacy.app.services import chatwoot_client

_ctx: dict = {}


def set_context(ctx: dict) -> None:
    _ctx.update(ctx)


@tool
async def escalate_to_human(
    summary: Annotated[str, "A brief summary of the conversation and why it needs human attention."],
    last_message: Annotated[str, "The user's last message."],
) -> str:
    """
    Escalate the conversation to a human agent immediately.
    Use when: user is very dissatisfied, topic is out of scope,
    user asks to speak with a person, or asks to stop receiving messages.
    """
    account_id = _ctx.get("account_id")
    conversation_id = _ctx.get("conversation_id")
    alert_conv_id = _ctx.get("alert_conversation_id")
    name = _ctx.get("contact_name", "Usuário")

    # Add label agente-off to stop bot from responding
    current_labels = _ctx.get("labels", [])
    new_labels = list(set(current_labels + ["agente-off"]))
    await chatwoot_client.update_labels(account_id, conversation_id, new_labels)

    # Notify manager if alert conversation is configured
    if alert_conv_id:
        alert_msg = (
            f"🚨 *Escalação humana*\n"
            f"Nome: {name}\n"
            f"Resumo: {summary}\n"
            f"Última mensagem: {last_message}"
        )
        await chatwoot_client.send_message(account_id, int(alert_conv_id), alert_msg)

    return "Conversation escalated. A human agent will take over shortly."
