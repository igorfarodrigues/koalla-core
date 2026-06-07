# Regras de Negócio

## Webhook e processamento

- Processar apenas eventos com `event = message_created`.
- Processar apenas mensagens `message_type = incoming`.
- Não responder quando houver labels bloqueadas:
  - `agente-off`
  - `gestor`
  - `testando-agente`

## Comandos especiais

- `/reset`: limpar memória da conversa, fila interna e estado de lock.
- `/teste`: ativar label `testando-agente` e confirmar no Chatwoot.

## Usuários

- Usuário é criado automaticamente no fluxo do webhook ao receber mensagem válida.
- Chave funcional principal: `wa_id` (telefone WhatsApp).

## Transações

- Movimentações: `CASH_IN` e `CASH_OUT`.
- Contexto de entidade: `PF` e `PJ`.
- Persistir dados no schema `koalla`.

## Escalação humana

- Fluxos de escalação devem respeitar `ALERT_CONVERSATION_ID` quando configurado.
