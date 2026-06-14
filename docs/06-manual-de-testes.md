# Manual de Testes (Local)

## 1. Subir ambiente

```bash
cp .env.example .env
docker-compose up -d --build
```

## 2. Verificar saúde da API

```bash
curl http://localhost:8000/health
```

Esperado:

```json
{"status":"ok","version":"0.2.2"}
```

## 3. Testar webhook (happy path)

```bash
curl -X POST http://localhost:8000/webhook/chatwoot \
  -H "Content-Type: application/json" \
  -d '{
    "id": 9001,
    "event": "message_created",
    "message_type": "incoming",
    "content": "Teste webhook",
    "account": {"id": 1, "name": "Koalla"},
    "sender": {"id": 101, "name": "Teste", "phone_number": "5511999999999", "email": "teste@example.com", "custom_attributes": {}},
    "conversation": {
      "id": 123,
      "labels": [],
      "custom_attributes": {},
      "contact_inbox": {"contact_id": 456, "source_id": "abc"},
      "meta": {"sender": {"phone_number": "5511999999999", "name": "Teste", "email": "teste@example.com", "custom_attributes": {}}},
      "messages": []
    },
    "attachments": []
  }'
```

Esperado:

```json
{"status":"queued"}
```

## 4. Testar evento ignorado

Enviar o mesmo payload com `"event": "conversation_updated"`.

Esperado:

```json
{"status":"ignored","event":"conversation_updated"}
```

## 5. Testar comandos de controle

- `/teste`: payload com `"content": "/teste"` e `"message_type": "incoming"`.
- `/reset`: payload com `"content": "/reset"` e `"message_type": "incoming"`.

## 6. Validar banco local

```bash
docker-compose exec db psql -U koalla -d koalla -c "SELECT id, wa_id, full_name FROM koalla.users ORDER BY created_at DESC LIMIT 10;"
```

## 7. Diagnóstico rápido

```bash
docker-compose ps
docker-compose logs --no-color --tail=100 api
```
