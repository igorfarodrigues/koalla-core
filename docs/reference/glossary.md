# Glossário

## Termos Técnicos

### A

**Agent**
Componente de IA que processa mensagens do usuário e decide quais ações tomar. No Koalla, implementado com Spring AI e OpenAI GPT.

**AgentContext**
Objeto que contém o contexto da conversa atual (userId, conversationId, contactId, etc.) passado para as tools durante execução.

**Asaas**
Plataforma de pagamentos usada para gerenciar cobranças e assinaturas. Responsável por processar pagamentos PIX e cartão de crédito.

### C

**CASH_IN**
Tipo de movimentação que representa entrada de dinheiro (receita, salário, etc.).

**CASH_OUT**
Tipo de movimentação que representa saída de dinheiro (despesa, gasto, etc.).

**Chatwoot**
Plataforma de atendimento ao cliente usada como canal de comunicação WhatsApp. Recebe mensagens do WhatsApp Business API e envia webhooks para o Koalla.

**ChatHistory**
Histórico de mensagens da conversa armazenado no banco de dados. Usado como memória do agente para contexto.

### D

**Debounce**
Técnica que agrupa múltiplas mensagens rápidas em uma única execução do agente. O sistema espera um intervalo (default: 2s) antes de processar.

### E

**Entity Type**
Contexto da transação: PF (Pessoa Física) ou PJ (Pessoa Jurídica).

**Escalation**
Processo de transferir a conversa para atendimento humano quando o bot não consegue resolver.

### F

**Function Calling**
Capacidade do modelo de IA de chamar funções definidas (tools) durante a geração de resposta. Permite que o agente execute ações como registrar transações.

### G

**Gateway**
Camada de abstração entre os services e APIs externas (Chatwoot, Asaas). Implementado em `gateway/`.

**Grace Period**
Período de carência após falha de pagamento antes de desativar a conta do usuário. Configurado em `grace_expires_at` na tabela de subscriptions.

### L

**Label**
Tag aplicada às conversas no Chatwoot. Usada para controle de fluxo:
- `agente-off`: Bot não responde
- `gestor`: Conversa de gestão
- `testando-agente`: Modo de teste

**Lock**
Mecanismo que previne processamento paralelo da mesma conversa. Garante que apenas uma instância do agente responda por vez.

### M

**Mapper**
Componente responsável por converter entre entidades JPA e modelos de domínio. Implementado em `mapper/`.

**MessageQueue**
Fila de mensagens aguardando processamento. Usada para debounce de mensagens rápidas.

**Movement**
Direção do fluxo financeiro: CASH_IN (entrada) ou CASH_OUT (saída).

### S

**Session**
Identificador único de conversa, geralmente o número WhatsApp (wa_id). Usado como chave para histórico e estado.

**Spring AI**
Framework do Spring para integração com modelos de IA. Fornece abstrações para chat, embeddings e function calling.

**Subscription**
Registro de assinatura do usuário. Estados possíveis:
- `TRIALING`: Período de teste
- `ACTIVE`: Assinatura ativa
- `PAST_DUE`: Pagamento atrasado (grace period)
- `CANCELED`: Cancelado pelo usuário
- `EXPIRED`: Expirado por falta de pagamento

**SubStatus**
Enum com os status possíveis de uma subscription: TRIALING, ACTIVE, PAST_DUE, CANCELED, EXPIRED.

### T

**Tool**
Função que o agente pode invocar durante processamento. Implementada como bean Spring com `@Description`. Exemplo: `registerTransaction`, `sendText`.

**ToolContextHolder**
Componente que mantém o contexto da conversa em ThreadLocal para acesso pelas tools durante execução.

**Transaction**
Registro financeiro do usuário contendo: descrição, valor, tipo (CASH_IN/CASH_OUT), categoria, data.

### W

**wa_id**
WhatsApp ID - número de telefone do usuário no formato internacional (ex: +5531999999999).

**Webhook**
Endpoint HTTP que recebe notificações do Chatwoot quando eventos ocorrem (nova mensagem, etc.).

**Whisper**
Modelo de transcrição de áudio da OpenAI. Usado para converter mensagens de voz em texto.

## Acrônimos

| Acrônimo | Significado |
|----------|-------------|
| API | Application Programming Interface |
| DTO | Data Transfer Object |
| JPA | Java Persistence API |
| LLM | Large Language Model |
| ORM | Object-Relational Mapping |
| PF | Pessoa Física |
| PJ | Pessoa Jurídica |
| REST | Representational State Transfer |
| UUID | Universally Unique Identifier |

## Valores Monetários

Todos os valores monetários são armazenados em **centavos** (Long):
- R$ 45,00 = 4500
- R$ 1.234,56 = 123456
- R$ 0,50 = 50

Isso evita problemas de arredondamento com ponto flutuante.

