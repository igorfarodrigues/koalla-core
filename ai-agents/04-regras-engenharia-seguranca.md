# Regras de Engenharia e Segurança

## Segurança de dados

- Nunca commitar chaves (`OPENAI_API_KEY`, `CHATWOOT_API_TOKEN`, `ASAAS_API_KEY`).
- Não logar payload completo com dados pessoais desnecessários.
- Tratar informações de usuário com princípio de mínimo acesso.

## Integrações externas

- Falhas de rede/API devem ser tratadas com erro explícito e contexto útil.
- Não mascarar erro crítico com fallback silencioso.
- Evitar retry infinito em integrações.

## Confiabilidade

- Fluxos assíncronos devem preservar consistência de lock/unlock de conversa.
- Evitar condições de corrida no processamento da fila de mensagens.
- Mudanças em pipeline devem manter comportamento de debounce.

## Regras de mudança

- Não alterar contratos públicos sem atualizar documentação.
- Não modificar arquivos fora do escopo da tarefa.
- Em mudanças de comportamento, atualizar o manual de testes.
