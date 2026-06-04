"""LangChain tools for financial transaction management (Carol's core functions)."""
from langchain_core.tools import tool
from typing import Annotated
from datetime import datetime, timezone
from sqlalchemy import select, func
from app.database import AsyncSessionLocal
from app.models.transaction import Transaction, Category, MovementType, EntityContext

_ctx: dict = {}


def set_context(ctx: dict) -> None:
    _ctx.update(ctx)


@tool
async def register_transaction(
    description: Annotated[str, "Short description of the transaction (e.g. 'Uber', 'Almoço')"],
    amount_cents: Annotated[int, "Amount in cents (e.g. 4500 for R$45,00)"],
    movement: Annotated[str, "CASH_IN for income, CASH_OUT for expense"],
    category_name: Annotated[str, "Category: Mercado, Diversao, Educação, Assinatura, Transporte, Alimentacao, Moradia, Lazer, Saude, Investimento, Outros, Receita"],
    occurred_at: Annotated[str | None, "ISO date string (YYYY-MM-DD). Omit for today."] = None,
) -> str:
    """
    Register a financial transaction for the user.
    Infer all fields from the user's message before calling this tool.
    """
    user_id = _ctx.get("user_id")
    if not user_id:
        return "Error: user not found."

    try:
        mov = MovementType(movement.upper())
    except ValueError:
        return f"Invalid movement type: {movement}"

    occurred = None
    if occurred_at:
        try:
            occurred = datetime.fromisoformat(occurred_at).replace(tzinfo=timezone.utc)
        except ValueError:
            occurred = None

    async with AsyncSessionLocal() as db:
        # Resolve category
        result = await db.execute(
            select(Category).where(
                (Category.name == category_name) &
                ((Category.user_id == None) | (Category.user_id == user_id))
            )
        )
        category = result.scalars().first()
        category_id = category.id if category else None

        transaction = Transaction(
            user_id=user_id,
            description=description,
            amount=amount_cents,
            movement=mov,
            category_id=category_id,
            entity_type=EntityContext.PF,
            source="whatsapp",
            occurred_at=occurred or datetime.now(timezone.utc),
        )
        db.add(transaction)
        await db.commit()
        await db.refresh(transaction)

    amount_brl = amount_cents / 100
    direction = "💰 Receita" if mov == MovementType.CASH_IN else "💸 Despesa"
    return f"{direction} de R${amount_brl:.2f} ({description}) registrada em {category_name}."


@tool
async def list_transactions(
    period: Annotated[str, "Period filter: 'today', 'week', 'month', or 'YYYY-MM' for a specific month"],
    category: Annotated[str | None, "Optional category filter"] = None,
) -> str:
    """List the user's financial transactions for a given period."""
    user_id = _ctx.get("user_id")
    if not user_id:
        return "Error: user not found."

    now = datetime.now(timezone.utc)
    if period == "today":
        start = now.replace(hour=0, minute=0, second=0, microsecond=0)
    elif period == "week":
        start = now.replace(hour=0, minute=0, second=0, microsecond=0)
        start = start.replace(day=now.day - now.weekday())
    elif period == "month":
        start = now.replace(day=1, hour=0, minute=0, second=0, microsecond=0)
    else:
        try:
            year, month = map(int, period.split("-"))
            start = datetime(year, month, 1, tzinfo=timezone.utc)
            if month == 12:
                end = datetime(year + 1, 1, 1, tzinfo=timezone.utc)
            else:
                end = datetime(year, month + 1, 1, tzinfo=timezone.utc)
        except Exception:
            return "Invalid period format. Use 'today', 'week', 'month', or 'YYYY-MM'."

    async with AsyncSessionLocal() as db:
        query = select(Transaction, Category).outerjoin(
            Category, Transaction.category_id == Category.id
        ).where(
            (Transaction.user_id == user_id) &
            (Transaction.occurred_at >= start)
        )
        if category:
            query = query.where(Category.name == category)

        result = await db.execute(query.order_by(Transaction.occurred_at.desc()).limit(20))
        rows = result.all()

    if not rows:
        return "Nenhuma transação encontrada nesse período."

    lines = []
    total_in = total_out = 0
    for tx, cat in rows:
        cat_name = cat.name if cat else "Sem categoria"
        symbol = "+" if tx.movement == MovementType.CASH_IN else "-"
        amount_brl = tx.amount / 100
        lines.append(f"{symbol}R${amount_brl:.2f} {tx.description or ''} ({cat_name})")
        if tx.movement == MovementType.CASH_IN:
            total_in += tx.amount
        else:
            total_out += tx.amount

    balance = (total_in - total_out) / 100
    summary = f"\nSaldo: R${balance:+.2f} | Entradas: R${total_in/100:.2f} | Saídas: R${total_out/100:.2f}"
    return "\n".join(lines) + summary


@tool
async def monthly_summary(
    month: Annotated[str | None, "Month in YYYY-MM format. Omit for current month."] = None,
) -> str:
    """Return a spending summary grouped by category for a given month."""
    user_id = _ctx.get("user_id")
    if not user_id:
        return "Error: user not found."

    now = datetime.now(timezone.utc)
    if not month:
        start = now.replace(day=1, hour=0, minute=0, second=0, microsecond=0)
        m, y = now.month, now.year
    else:
        try:
            y, m = map(int, month.split("-"))
            start = datetime(y, m, 1, tzinfo=timezone.utc)
        except Exception:
            return "Invalid month format. Use YYYY-MM."

    end = datetime(y + (m // 12), (m % 12) + 1, 1, tzinfo=timezone.utc)

    async with AsyncSessionLocal() as db:
        result = await db.execute(
            select(Category.name, Transaction.movement, func.sum(Transaction.amount))
            .outerjoin(Category, Transaction.category_id == Category.id)
            .where(
                (Transaction.user_id == user_id) &
                (Transaction.occurred_at >= start) &
                (Transaction.occurred_at < end)
            )
            .group_by(Category.name, Transaction.movement)
        )
        rows = result.all()

    if not rows:
        return f"Sem transações em {y}-{m:02d}."

    by_cat: dict[str, dict] = {}
    total_in = total_out = 0
    for cat_name, movement, total in rows:
        key = cat_name or "Sem categoria"
        if key not in by_cat:
            by_cat[key] = {"in": 0, "out": 0}
        if movement == MovementType.CASH_IN:
            by_cat[key]["in"] += total
            total_in += total
        else:
            by_cat[key]["out"] += total
            total_out += total

    lines = [f"📊 Resumo {y}-{m:02d}"]
    for cat, vals in sorted(by_cat.items(), key=lambda x: -x[1]["out"]):
        if vals["out"]:
            lines.append(f"  {cat}: -R${vals['out']/100:.2f}")
    lines.append(f"\nTotal saídas: R${total_out/100:.2f}")
    lines.append(f"Total entradas: R${total_in/100:.2f}")
    lines.append(f"Saldo: R${(total_in-total_out)/100:+.2f}")
    return "\n".join(lines)
