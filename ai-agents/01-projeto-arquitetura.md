# Projeto e Arquitetura

## Objetivo

API backend da assistente financeira Koalla via WhatsApp, com ingestão de webhook do Chatwoot, processamento por agente LLM e persistência em PostgreSQL.

## Stack

- Python 3.12
- FastAPI + SQLAlchemy async
- PostgreSQL (schema `koalla`)
- LangChain + OpenAI
- Docker / Docker Compose

## Arquitetura lógica

1. **Routers** recebem requisições HTTP.
2. **Schemas** validam payloads de entrada/saída.
3. **Services** executam a lógica de negócio e integrações externas.
4. **Models** persistem dados no banco.
5. **Tools** expõem capacidades usadas pelo agente LLM.

## Fluxo principal (webhook Chatwoot)

1. Recebe evento em `POST /webhook/chatwoot`.
2. Aceita apenas `event == message_created`.
3. Processa em background via `process_webhook`.
4. Executa filtros, debounce, lock de conversa e chamada do agente.
5. Envia resposta de volta para Chatwoot e libera lock.
