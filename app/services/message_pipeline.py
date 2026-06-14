"""
Core message processing pipeline.
Mirrors the full n8n workflow execution order:
  Info → Reset/Teste → Filter → Type → Queue → Debounce → Lock → Agent → Response
"""
import asyncio
from datetime import datetime, timezone

from sqlalchemy import select, delete
from sqlalchemy.dialects.postgresql import insert as pg_insert

from app.config import get_settings
from app.database import AsyncSessionLocal
from app.models.message import MessageQueue, ConversationStatus
from app.models.user import User
from app.schemas.chatwoot import ChatwootWebhookBody
from app.services import chatwoot_client, audio_service, agent_service

settings = get_settings()

# Labels that prevent the bot from responding
BLOCKED_LABELS = {"agente-off", "gestor", "testando-agente"}


# ── Helpers ────────────────────────────────────────────────────────────────

async def _get_user(wa_id: str) -> User | None:
    """Retorna o usuário existente ou None. NÃO cria automaticamente."""
    async with AsyncSessionLocal() as db:
        result = await db.execute(select(User).where(User.wa_id == wa_id))
        return result.scalars().first()


async def _enqueue_message(wa_id: str, message_id: str, message: str) -> None:
    async with AsyncSessionLocal() as db:
        db.add(MessageQueue(
            wa_id=wa_id,
            message_id=message_id,
            message=message,
            timestamp=datetime.now(timezone.utc),
        ))
        await db.commit()


async def _fetch_queue(wa_id: str) -> list[MessageQueue]:
    async with AsyncSessionLocal() as db:
        result = await db.execute(
            select(MessageQueue)
            .where(MessageQueue.wa_id == wa_id)
            .order_by(MessageQueue.timestamp)
        )
        return list(result.scalars().all())


async def _clear_queue(wa_id: str) -> None:
    async with AsyncSessionLocal() as db:
        await db.execute(delete(MessageQueue).where(MessageQueue.wa_id == wa_id))
        await db.commit()


async def _get_status(session_id: str) -> ConversationStatus | None:
    async with AsyncSessionLocal() as db:
        result = await db.execute(
            select(ConversationStatus).where(ConversationStatus.session_id == session_id)
        )
        return result.scalars().first()


async def _lock_conversation(session_id: str) -> None:
    async with AsyncSessionLocal() as db:
        stmt = pg_insert(ConversationStatus).values(
            session_id=session_id,
            lock_conversa=True,
            aguardando_followup=True,
            numero_followup=0,
            updated_at=datetime.now(timezone.utc),
        ).on_conflict_do_update(
            index_elements=["session_id"],
            set_={"lock_conversa": True, "aguardando_followup": True,
                  "numero_followup": 0, "updated_at": datetime.now(timezone.utc)},
        )
        await db.execute(stmt)
        await db.commit()


async def _unlock_conversation(session_id: str) -> None:
    async with AsyncSessionLocal() as db:
        stmt = pg_insert(ConversationStatus).values(
            session_id=session_id,
            lock_conversa=False,
            aguardando_followup=False,
            numero_followup=0,
            updated_at=datetime.now(timezone.utc),
        ).on_conflict_do_update(
            index_elements=["session_id"],
            set_={"lock_conversa": False, "aguardando_followup": False,
                  "updated_at": datetime.now(timezone.utc)},
        )
        await db.execute(stmt)
        await db.commit()


async def _reset_conversation(session_id: str, body: ChatwootWebhookBody) -> None:
    """Full reset: clear memory, queue, labels, contact attributes."""
    from sqlalchemy import delete as sa_delete
    from app.models.message import ChatHistory

    account_id = body.account.id
    conversation_id = body.conversation.id
    contact_id = body.conversation.contact_inbox.contact_id

    async with AsyncSessionLocal() as db:
        # Clear chat history (memory)
        await db.execute(
            sa_delete(ChatHistory).where(ChatHistory.session_id == session_id)
        )
        await db.commit()

    # Clear queue
    await _clear_queue(session_id)

    # Reset conversation status
    await _unlock_conversation(session_id)

    # Chatwoot: remove custom attributes
    if account_id and contact_id:
        await chatwoot_client.destroy_contact_attributes(
            account_id, contact_id,
            ["preferencia_audio_texto", "asaas_id_cliente", "asaas_id_cobranca", "asaas_status_cobranca"],
        )

    # Chatwoot: remove agente-off label
    if account_id and conversation_id:
        current_labels = body.conversation.labels
        clean_labels = [l for l in current_labels if l != "agente-off"]
        await chatwoot_client.update_labels(account_id, conversation_id, clean_labels)
        await chatwoot_client.send_message(account_id, conversation_id, "Memória resetada.")


# ── Main entry point ───────────────────────────────────────────────────────

async def process_webhook(body: ChatwootWebhookBody) -> None:
    """
    Entry point called by the webhook router.
    Mirrors the full n8n flow end-to-end.
    """
    # ── 1. Extract info ────────────────────────────────────────────────────
    message_type = body.message_type or ""
    labels = set(body.conversation.labels)
    wa_id = body.conversation.meta.sender.phone_number or ""
    name = body.sender.name
    content = body.content or ""
    message_id = str(body.id or "")
    account_id = body.account.id or settings.CHATWOOT_ACCOUNT_ID
    conversation_id = body.conversation.id
    contact_id = body.conversation.contact_inbox.contact_id
    contact_attrs = body.conversation.custom_attributes

    # Audio flag from first attachment
    is_audio = False
    attachment = None
    if body.attachments:
        attachment = body.attachments[0]
        is_audio = attachment.is_recorded_audio
    elif body.conversation.messages:
        msgs = body.conversation.messages
        if msgs and msgs[0].get("attachments"):
            att = msgs[0]["attachments"][0]
            is_audio = att.get("meta", {}).get("is_recorded_audio", False)

    session_id = wa_id  # mirrors n8n session key

    # ── 2. Route: /reset ───────────────────────────────────────────────────
    if content.lower().strip() == "/reset":
        await _reset_conversation(session_id, body)
        return

    # ── 3. Route: /teste ──────────────────────────────────────────────────
    if content.lower().strip() == "/teste" and account_id and conversation_id:
        new_labels = list(set(labels) | {"testando-agente"})
        await chatwoot_client.update_labels(account_id, conversation_id, new_labels)
        await chatwoot_client.send_message(account_id, conversation_id, "Modo de teste habilitado.")
        return

    # ── 4. Filter: only process valid incoming messages ────────────────────
    if message_type != "incoming":
        return
    if labels & BLOCKED_LABELS:
        return

    # ── 5. Gate: usuário deve estar cadastrado e ativo ────────────────────
    user = await _get_user(wa_id)

    if user is None:
        if account_id and conversation_id:
            await chatwoot_client.send_message(
                account_id,
                conversation_id,
                "👋 Olá! Para usar o Koalla, cadastre-se em *koalla.ai*\n\n"
                "É rápido e o primeiro acesso é grátis por 7 dias 🐨",
            )
        return

    if not user.is_active:
        if account_id and conversation_id:
            await chatwoot_client.send_message(
                account_id,
                conversation_id,
                "Sua assinatura está inativa. Para continuar usando o Koalla, "
                "acesse *koalla.ai/planos* e escolha um plano 🐨",
            )
        return

    # ── 6. Resolve message text (text / file / audio) ─────────────────────
    file_info = ""
    if attachment and not is_audio and attachment.file_type:
        file_info = f"\n<usuário enviou um arquivo do tipo '{attachment.file_type}'>"

    if is_audio and attachment and attachment.data_url:
        audio_bytes = await chatwoot_client.download_attachment(attachment.data_url)
        content = await audio_service.transcribe(audio_bytes)
    elif not content and file_info:
        content = file_info
    else:
        content = (content or "") + file_info

    # ── 7. Enqueue message ────────────────────────────────────────────────
    await _enqueue_message(session_id, message_id, content)

    # ── 8. Debounce: wait then check if this is still the latest message ──
    await asyncio.sleep(settings.MESSAGE_QUEUE_WAIT_SECONDS)

    queue = await _fetch_queue(session_id)
    if not queue or queue[-1].message_id != message_id:
        # A newer message arrived — let that execution handle it
        return

    # ── 9. Conversation lock: wait if agent is already responding ─────────
    for _ in range(5):
        status = await _get_status(session_id)
        if not status or not status.lock_conversa:
            break
        await asyncio.sleep(settings.MESSAGE_QUEUE_WAIT_SECONDS * 10)
    else:
        # Gave up waiting
        return

    # ── 10. Lock conversation & clear queue ───────────────────────────────
    await _lock_conversation(session_id)
    await _clear_queue(session_id)

    # Collect all queued messages into one
    combined_message = "\n".join(m.message for m in queue)

    # ── 11. Mark as read ──────────────────────────────────────────────────
    if account_id and conversation_id:
        await chatwoot_client.mark_as_read(account_id, conversation_id)

    # ── 12. Build agent context ───────────────────────────────────────────
    # Get fresh contact attributes
    contact_data = {}
    if account_id and contact_id:
        try:
            contact_data = await chatwoot_client.get_contact(account_id, contact_id)
        except Exception:
            pass

    contact_custom_attrs = contact_data.get("payload", {}).get("custom_attributes", contact_attrs)
    audio_pref = contact_custom_attrs.get("preferencia_audio_texto")

    agent_context = {
        "user_id": user.id,
        "wa_id": wa_id,
        "account_id": account_id,
        "conversation_id": conversation_id,
        "contact_id": contact_id,
        "message_id": int(message_id) if message_id.isdigit() else 0,
        "contact_name": name or wa_id,
        "labels": list(labels),
        "contact_custom_attributes": contact_custom_attrs,
        "alert_conversation_id": settings.ALERT_CONVERSATION_ID,
    }

    # ── 13. Run agent ─────────────────────────────────────────────────────
    try:
        output = await agent_service.run_agent(combined_message, session_id, agent_context)
    except Exception as e:
        await _unlock_conversation(session_id)
        raise

    if not output or output == "Agent stopped due to max iterations.":
        await _unlock_conversation(session_id)
        return

    # ── 14. Format output for WhatsApp ────────────────────────────────────
    formatted = await agent_service.format_for_whatsapp(output)

    # ── 15. Send response ─────────────────────────────────────────────────
    # Break into chunks ≤ 4096 chars and send sequentially
    if account_id and conversation_id:
        chunks = _split_message(formatted)
        for chunk in chunks:
            await chatwoot_client.send_message(account_id, conversation_id, chunk)

    # ── 16. Unlock ────────────────────────────────────────────────────────
    await _unlock_conversation(session_id)


def _split_message(text: str, max_len: int = 4096) -> list[str]:
    """Split a long message into WhatsApp-safe chunks at sentence boundaries."""
    if len(text) <= max_len:
        return [text]

    chunks = []
    while text:
        if len(text) <= max_len:
            chunks.append(text)
            break
        split_at = text.rfind("\n\n", 0, max_len)
        if split_at == -1:
            split_at = text.rfind(". ", 0, max_len)
        if split_at == -1:
            split_at = max_len
        chunks.append(text[:split_at].strip())
        text = text[split_at:].strip()
    return chunks
