# Koalla Agent - Produto

## Visão Geral

O Koalla é um assistente financeiro pessoal via WhatsApp que ajuda usuários a registrar, organizar e entender sua vida financeira com o mínimo de fricção.

## Proposta de Valor

- **Registro automático**: Mensagens curtas como "Almoço 45" são automaticamente registradas como transações
- **Inferência inteligente**: Categorização e tipo de movimento inferidos do contexto
- **Mínima fricção**: Não pede confirmação se a inferência é clara
- **Histórico completo**: Consulta de transações e resumos por período
- **Voz**: Suporte a mensagens de áudio via WhatsApp

## Funcionalidades

### 1. Registro de Transações

**Input do usuário**:
- "Almoço 45"
- "Uber 34"
- "Recebi 500 do freela ontem"
- "Netflix 55,90"

**Comportamento do agente**:
1. Identifica valor monetário
2. Infere tipo (CASH_IN/CASH_OUT)
3. Infere categoria
4. Infere data (hoje, ontem, etc.)
5. Registra automaticamente
6. Confirma com emoji + resumo curto

### 2. Consulta de Transações

**Comandos**:
- "Minhas transações de hoje"
- "Gastos da semana"
- "Quanto gastei em junho"
- "Lista transporte do mês"

**Resposta**: Lista formatada com totais

### 3. Resumo Mensal

**Comandos**:
- "Resumo do mês"
- "Como estou em maio"
- "Balanço de 2024-03"

**Resposta**: Breakdown por categoria com totais

### 4. Preferências

**Comandos**:
- "Prefiro receber áudio"
- "Só texto por favor"

**Ação**: Salva preferência no contato

### 5. Escalação

**Gatilhos**:
- "Quero falar com humano"
- Insatisfação detectada
- Assunto fora do escopo

**Ação**: 
- Adiciona label "agente-off"
- Notifica gestor
- Bot para de responder

## Categorias Disponíveis

| Categoria | Descrição | Exemplos |
|-----------|-----------|----------|
| Alimentacao | Refeições e comida | Almoço, janta, lanche |
| Transporte | Locomoção | Uber, 99, gasolina |
| Mercado | Compras de supermercado | Mercado, feira |
| Moradia | Custos de moradia | Aluguel, condomínio, luz |
| Saude | Saúde e bem-estar | Médico, farmácia |
| Educação | Educação e cursos | Curso, livro, escola |
| Lazer | Entretenimento | Cinema, show, bar |
| Diversao | Diversão geral | Jogos, festas |
| Assinatura | Serviços recorrentes | Netflix, Spotify |
| Investimento | Investimentos | Ações, fundos |
| Receita | Entradas de dinheiro | Salário, freela |
| Outros | Não categorizado | - |

## Regras de Negócio

### Inferência de Tipo

| Padrão | Tipo |
|--------|------|
| Default | CASH_OUT |
| recebi, salário, pix recebido, entrou, freela | CASH_IN |

### Inferência de Data

| Padrão | Data |
|--------|------|
| Default | Hoje |
| ontem | D-1 |
| anteontem | D-2 |
| semana passada | (não implementado) |

### Comportamento

1. **Se inferência clara**: Executa e confirma
2. **Se ambíguo**: Pergunta UMA vez
3. **Nunca**: Pede confirmação desnecessária

## Comandos Especiais

| Comando | Ação |
|---------|------|
| `/reset` | Limpa memória da conversa |
| `/teste` | Ativa modo de teste |

## Limitações Conhecidas

1. Não suporta transações recorrentes automáticas
2. Não integra com bancos (Open Banking)
3. Não gera relatórios em PDF
4. Histórico limitado a últimas 100 mensagens
5. Não suporta múltiplas moedas

## Roadmap

### v0.4.0
- [ ] Transações recorrentes
- [ ] Metas de economia
- [ ] Alertas de gastos

### v0.5.0
- [ ] Integração Open Banking
- [ ] Importação de extratos
- [ ] Relatórios PDF

### v1.0.0
- [ ] Multi-moeda
- [ ] Contas compartilhadas
- [ ] API pública

