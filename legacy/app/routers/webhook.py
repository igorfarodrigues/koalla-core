"""
POST /webhook/chatwoot — receives Chatwoot webhook events.

Melhorias de segurança:
- Validação de segredo compartilhado
- Validação de JSON
- Processamento assíncrono em background
"""

import secrets

from fastapi import APIRouter, Request, BackgroundTasks, HTTPException

from legacy.app.config import get_settings
from legacy.app.schemas.chatwoot import ChatwootWebhookBody
from legacy.app.services.message_pipeline import process_webhook

router = APIRouter()

settings = get_settings()


@router.post("/chatwoot")
async def chatwoot_webhook(
    request: Request,
    background_tasks: BackgroundTasks,
) -> dict:
    """
    Recebe webhooks do Chatwoot.

    Segurança:
    Verifica se o header X-Koalla-Secret possui
    o mesmo valor configurado no .env.

    Isso impede que terceiros enviem requisições
    falsas para o webhook.
    """

    # =====================================================================
    # SEGURANÇA DO WEBHOOK
    # =====================================================================
    # O Chatwoot deverá enviar:
    #
    # X-Koalla-Secret: valor_do_env
    #
    # Caso o valor seja diferente:
    # HTTP 401 Unauthorized
    # =====================================================================

    secret = request.headers.get("X-Koalla-Secret")

    if not secrets.compare_digest(
        secret or "",
        settings.CHATWOOT_WEBHOOK_SECRET
    ):
        raise HTTPException(
            status_code=401,
            detail="Unauthorized webhook"
        )

    # =====================================================================
    # LEITURA DO JSON
    # =====================================================================

    try:
        data = await request.json()
    except Exception:
        raise HTTPException(
            status_code=400,
            detail="Invalid JSON payload"
        )

    # =====================================================================
    # VALIDAÇÃO DO PAYLOAD
    # =====================================================================

    try:
        body = ChatwootWebhookBody(**data)
    except Exception:
        raise HTTPException(
         status_code=400,
         detail="Invalid webhook payload"
        )

    # =====================================================================
    # PROCESSA APENAS EVENTOS message_created
    # =====================================================================

    if body.event != "message_created":
        return {
            "status": "ignored",
            "event": body.event
        }

    # =====================================================================
    # PROCESSAMENTO EM BACKGROUND
    # =====================================================================

    background_tasks.add_task(
        process_webhook,
        body
    )

    return {
        "status": "queued"
    }