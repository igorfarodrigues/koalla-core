"""Chatwoot API client — mirrors all HTTP calls from the n8n workflow."""
import httpx
from app.config import get_settings

settings = get_settings()


def _headers() -> dict:
    return {
        "api_access_token": settings.CHATWOOT_API_TOKEN,
        "Content-Type": "application/json",
    }


def _base(account_id: int) -> str:
    return f"{settings.CHATWOOT_URL}/api/v1/accounts/{account_id}"


async def mark_as_read(account_id: int, conversation_id: int) -> None:
    """POST update_last_seen — marks messages as read."""
    url = f"{_base(account_id)}/conversations/{conversation_id}/update_last_seen"
    async with httpx.AsyncClient() as client:
        await client.post(url, headers=_headers())


async def send_message(account_id: int, conversation_id: int, content: str) -> dict:
    """POST a text message to a conversation."""
    url = f"{_base(account_id)}/conversations/{conversation_id}/messages"
    async with httpx.AsyncClient() as client:
        r = await client.post(url, headers=_headers(), json={"content": content})
        r.raise_for_status()
        return r.json()


async def send_reaction(
    account_id: int,
    conversation_id: int,
    message_id: int,
    emoji: str,
) -> dict:
    """POST a reaction to a specific message."""
    url = f"{_base(account_id)}/conversations/{conversation_id}/messages"
    body = {
        "content": emoji,
        "content_attributes": {
            "in_reply_to": message_id,
            "is_reaction": True,
        },
    }
    async with httpx.AsyncClient() as client:
        r = await client.post(url, headers=_headers(), json=body)
        r.raise_for_status()
        return r.json()


async def update_labels(account_id: int, conversation_id: int, labels: list[str]) -> None:
    """POST/replace conversation labels."""
    url = f"{_base(account_id)}/conversations/{conversation_id}/labels"
    async with httpx.AsyncClient() as client:
        await client.post(url, headers=_headers(), json={"labels": labels})


async def get_contact(account_id: int, contact_id: int) -> dict:
    """GET contact details (includes custom_attributes)."""
    url = f"{_base(account_id)}/contacts/{contact_id}"
    async with httpx.AsyncClient() as client:
        r = await client.get(url, headers=_headers())
        r.raise_for_status()
        return r.json()


async def update_contact_attributes(
    account_id: int,
    contact_id: int,
    custom_attributes: dict,
) -> dict:
    """PATCH contact custom attributes."""
    url = f"{_base(account_id)}/contacts/{contact_id}"
    async with httpx.AsyncClient() as client:
        r = await client.patch(
            url,
            headers=_headers(),
            json={"custom_attributes": custom_attributes},
        )
        r.raise_for_status()
        return r.json()


async def destroy_contact_attributes(
    account_id: int,
    contact_id: int,
    attributes: list[str],
) -> None:
    """POST destroy_custom_attributes."""
    url = f"{_base(account_id)}/contacts/{contact_id}/destroy_custom_attributes"
    async with httpx.AsyncClient() as client:
        await client.post(
            url, headers=_headers(), json={"custom_attributes": attributes}
        )


async def download_attachment(data_url: str) -> bytes:
    """Download a binary attachment (audio, file) from Chatwoot storage."""
    async with httpx.AsyncClient() as client:
        r = await client.get(data_url, headers=_headers(), follow_redirects=True)
        r.raise_for_status()
        return r.content
