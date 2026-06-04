"""
POST /webhook/chatwoot — receives Chatwoot webhook events.
Mirrors the n8n webhook trigger node.
"""
import asyncio
from fastapi import APIRouter, Request, BackgroundTasks, HTTPException
from app.schemas.chatwoot import ChatwootWebhookBody
from app.services.message_pipeline import process_webhook

router = APIRouter()


@router.post("/chatwoot")
async def chatwoot_webhook(
    request: Request,
    background_tasks: BackgroundTasks,
) -> dict:
    """
    Receive a Chatwoot message_created webhook.
    Processing runs in the background so Chatwoot gets an immediate 200.
    """
    try:
        data = await request.json()
    except Exception:
        raise HTTPException(status_code=400, detail="Invalid JSON payload")

    body = ChatwootWebhookBody(**data)

    # Only handle message_created events
    if body.event != "message_created":
        return {"status": "ignored", "event": body.event}

    background_tasks.add_task(process_webhook, body)
    return {"status": "queued"}
