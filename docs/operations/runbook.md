# Runbook de Operações

## Comandos Úteis

### Verificar Status

```bash
# Health check
curl http://localhost:8080/actuator/health

# Info da aplicação
curl http://localhost:8080/actuator/info

# Métricas
curl http://localhost:8080/actuator/metrics
```

### Logs

```bash
# Docker Compose
docker-compose logs -f api

# Kubernetes
kubectl logs -f deployment/koalla-core -n koalla

# Filtrar por nível
docker-compose logs api | grep ERROR
```

### Database

```bash
# Conectar ao PostgreSQL
docker-compose exec db psql -U koalla -d koalla

# Queries úteis
psql> \dt koalla.*                    # Listar tabelas
psql> SELECT COUNT(*) FROM koalla.users;
psql> SELECT * FROM koalla.conversation_status WHERE lock_conversa = true;
```

## Troubleshooting

### 1. Bot não está respondendo

**Sintomas**: Mensagens no WhatsApp não geram resposta

**Verificar**:
```sql
-- Verificar locks travados
SELECT * FROM koalla.conversation_status 
WHERE lock_conversa = true 
AND updated_at < NOW() - INTERVAL '5 minutes';

-- Limpar locks antigos
UPDATE koalla.conversation_status 
SET lock_conversa = false 
WHERE updated_at < NOW() - INTERVAL '10 minutes';
```

```bash
# Verificar labels no Chatwoot
# Se tiver "agente-off", remover manualmente
```

**Causas comuns**:
- Lock de conversa travado
- Label "agente-off" ativa
- Usuário inativo
- Erro no webhook

### 2. Erro de transcrição de áudio

**Sintomas**: Áudios retornam "mensagem não audível"

**Verificar**:
```bash
# Logs de erro
docker-compose logs api | grep "transcription failed"

# Testar API OpenAI
curl https://api.openai.com/v1/models \
  -H "Authorization: Bearer $OPENAI_API_KEY"
```

**Causas comuns**:
- API key inválida ou expirada
- Cota OpenAI excedida
- Formato de áudio não suportado

### 3. Agent não registra transações

**Sintomas**: Mensagens como "Almoço 45" não criam registros

**Verificar**:
```sql
-- Verificar transações recentes
SELECT * FROM koalla.transactions 
ORDER BY created_at DESC LIMIT 10;

-- Verificar histórico do chat
SELECT * FROM koalla.chat_history 
WHERE session_id = '+5531999999999'
ORDER BY created_at DESC LIMIT 20;
```

**Causas comuns**:
- Function calling não acionado pelo modelo
- Categoria não encontrada
- Erro de parsing de valor

### 4. Webhook não está chegando

**Sintomas**: Chatwoot mostra mensagens mas API não processa

**Verificar**:
```bash
# Verificar endpoint
curl -X POST http://localhost:8080/webhook/chatwoot \
  -H "Content-Type: application/json" \
  -d '{"event": "message_created", "message_type": "incoming"}'

# Verificar configuração no Chatwoot
# Settings > Integrations > Webhooks
```

**Causas comuns**:
- URL do webhook incorreta
- Firewall bloqueando
- Certificado SSL inválido

### 5. Erro de memória / OOM

**Sintomas**: Container reiniciando, logs de OutOfMemoryError

**Ações**:
```bash
# Verificar uso de memória
docker stats koalla-core

# Aumentar limites
# docker-compose.yml:
services:
  api:
    deploy:
      resources:
        limits:
          memory: 1G
```

## Procedimentos de Emergência

### Desligar o Bot

```bash
# Opção 1: Parar o serviço
docker-compose stop api

# Opção 2: Adicionar label "agente-off" em todas as conversas via Chatwoot
```

### Resetar Usuário Específico

```sql
-- Limpar memória
DELETE FROM koalla.chat_history WHERE session_id = '+5531999999999';

-- Limpar fila
DELETE FROM koalla.message_queue WHERE wa_id = '+5531999999999';

-- Limpar lock
DELETE FROM koalla.conversation_status WHERE session_id = '+5531999999999';
```

### Backup do Banco

```bash
# Dump completo
docker-compose exec db pg_dump -U koalla koalla > backup.sql

# Apenas schema koalla
docker-compose exec db pg_dump -U koalla -n koalla koalla > backup_schema.sql
```

### Restore do Banco

```bash
# Restore completo
docker-compose exec -T db psql -U koalla koalla < backup.sql
```

## Contatos

| Tipo | Contato |
|------|---------|
| Suporte técnico | devops@koalla.ai |
| Emergência | +55 31 99999-9999 |
| Chatwoot | admin@chatwoot.koalla.ai |

