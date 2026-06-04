"""
Carol — the AI financial assistant.
Mirrors the n8n 'assistente v0.2.0' agent node with LangChain.
"""
import json
from datetime import datetime, timezone
from langchain_openai import ChatOpenAI
from langchain.agents import AgentExecutor, create_tool_calling_agent
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder
from langchain_postgres import PostgresChatMessageHistory
from langchain_core.messages import SystemMessage

from app.config import get_settings
from app.tools.transaction_tools import (
    register_transaction, list_transactions, monthly_summary, set_context as tx_ctx
)
from app.tools.chatwoot_tools import (
    send_text, react_to_message, set_response_preference,
    send_cancellation_alert, set_context as cw_ctx
)
from app.tools.escalation_tools import escalate_to_human, set_context as esc_ctx

settings = get_settings()

SYSTEM_PROMPT = """\
# PAPEL
Você é a Carol, Assistente Financeira pessoal do usuário. Seu papel é fazer a gestão \
financeira ativa e automática: registrar gastos e receitas, categorizar transações, \
acompanhar histórico financeiro e fornecer clareza sobre para onde o dinheiro está indo \
— com o mínimo de fricção possível.

# PRINCÍPIO FUNDAMENTAL
Carol NÃO é uma atendente passiva. Carol é um motor financeiro inteligente.
Seu objetivo é:
* Registrar transações automaticamente
* Inferir dados sem perguntar quando possível
* Reduzir esforço cognitivo do usuário
* Evitar perguntas desnecessárias

# CONTEXTO DINÂMICO
* Data atual: {current_date}
* Moeda padrão: BRL (R$)

# REGRAS DE INFERÊNCIA (AUTOMAÇÃO OBRIGATÓRIA)
Ao receber qualquer mensagem curta contendo valor monetário, Carol DEVE assumir que se \
trata de um registro financeiro.

### Categorias disponíveis:
Mercado, Diversao, Educação, Assinatura, Transporte, Alimentacao, Moradia, Lazer, \
Saude, Investimento, Outros, Receita

### Inferência de tipo:
* Padrão: CASH_OUT (despesa)
* Só usar CASH_IN se houver: recebi, salário, pix recebido, entrou, freela

# COMPORTAMENTO PADRÃO (EXEMPLOS)
* "Almoço 45" → registrar: Almoço, R$45, Alimentacao, hoje, CASH_OUT → "✅ Almoço R$45 em Alimentação."
* "Recebi 500 do freela ontem" → registrar: Freela, R$500, Receita, ontem, CASH_IN → "💰 Receita R$500 registrada para ontem."
* "Uber 34" → registrar → "🚗 Uber R$34 em Transporte."

# REGRAS DE COMUNICAÇÃO
* Respostas curtas e confirmatórias
* Emojis apenas para confirmação visual
* Nunca listar regras internas ao usuário
* Nunca pedir confirmação se a inferência for clara

# PERGUNTAS PERMITIDAS (SÓ QUANDO NECESSÁRIO)
Só perguntar se faltar informação CRÍTICA (valor não identificado ou mensagem ambígua).

# FERRAMENTAS DISPONÍVEIS
- register_transaction: registra receita ou despesa
- list_transactions: lista transações por período
- monthly_summary: resumo mensal por categoria
- send_text: envia texto separado (use para links, PIX, etc.)
- react_to_message: reação emoji (máx 3/conversa)
- set_response_preference: salva preferência audio/texto
- send_cancellation_alert: alerta de cancelamento ao gestor
- escalate_to_human: escalar para atendimento humano

# REGRA DE OURO
Se deu para inferir, EXECUTE. Se não deu, pergunte UMA vez."""


def _build_agent(context: dict) -> AgentExecutor:
    """Build and return a configured AgentExecutor for Carol."""
    # Inject context into all tools
    tx_ctx(context)
    cw_ctx(context)
    esc_ctx(context)

    tools = [
        register_transaction,
        list_transactions,
        monthly_summary,
        send_text,
        react_to_message,
        set_response_preference,
        send_cancellation_alert,
        escalate_to_human,
    ]

    llm = ChatOpenAI(
        model=settings.OPENAI_MODEL,
        api_key=settings.OPENAI_API_KEY,
        temperature=0,
    )

    prompt = ChatPromptTemplate.from_messages([
        ("system", SYSTEM_PROMPT.format(
            current_date=datetime.now(timezone.utc).strftime("%Y-%m-%d")
        )),
        MessagesPlaceholder("chat_history"),
        ("human", "{input}"),
        MessagesPlaceholder("agent_scratchpad"),
    ])

    agent = create_tool_calling_agent(llm, tools, prompt)
    return AgentExecutor(
        agent=agent,
        tools=tools,
        max_iterations=settings.AGENT_MAX_ITERATIONS,
        return_intermediate_steps=True,
        handle_parsing_errors=True,
    )


async def run_agent(message: str, session_id: str, context: dict) -> str:
    """
    Run the Carol agent for a given message and session.
    Returns the agent's text output.
    """
    # Load conversation memory from Postgres
    history = PostgresChatMessageHistory(
        connection_string=settings.DATABASE_URL.replace("+asyncpg", ""),
        session_id=session_id,
        table_name="koalla.chat_history",
    )
    chat_history = history.messages[-settings.MEMORY_WINDOW_LENGTH:]

    agent_executor = _build_agent(context)
    result = await agent_executor.ainvoke({
        "input": message,
        "chat_history": chat_history,
    })

    output = result.get("output", "")

    # Persist AI response to history
    history.add_user_message(message)
    history.add_ai_message(output)

    return output


async def format_for_whatsapp(text: str) -> str:
    """
    Format agent output for WhatsApp:
    - Replace ** with *
    - Remove #
    - Remove emojis
    Mirrors the n8n 'Formatar texto' node.
    """
    from openai import AsyncOpenAI
    client = AsyncOpenAI(api_key=settings.OPENAI_API_KEY)

    response = await client.chat.completions.create(
        model=settings.OPENAI_FORMATTING_MODEL,
        messages=[
            {
                "role": "system",
                "content": (
                    "Você é especialista em formatação de mensagem para WhatsApp, "
                    "trabalhando somente na formatação e não alterando o conteúdo da mensagem.\n"
                    "- Substitua ** por *\n"
                    "- Remova #\n"
                    "- Remova emojis\n\n"
                    "SUA SAÍDA DEVE SER SOMENTE A MENSAGEM FORMATADA."
                ),
            },
            {"role": "user", "content": text},
        ],
    )
    return response.choices[0].message.content or text
