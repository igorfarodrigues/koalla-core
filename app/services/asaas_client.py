"""
Asaas billing API client.
Cobre: customers, subscriptions (recorrente com trial) e payments.
"""
import httpx
from datetime import date, timedelta
from app.config import get_settings

settings = get_settings()

PLAN_LABELS = {
    "STARTER": "Koalla Starter",
    "PRO": "Koalla Pro",
    "BUSINESS": "Koalla Business",
}

PLAN_VALUES = {
    "STARTER": settings.PLAN_STARTER_VALUE,
    "PRO": settings.PLAN_PRO_VALUE,
    "BUSINESS": settings.PLAN_BUSINESS_VALUE,
}


def _headers() -> dict:
    return {
        "access_token": settings.ASAAS_API_KEY,
        "Content-Type": "application/json",
    }


# ── Customers ──────────────────────────────────────────────────────────────

async def get_or_create_customer(
    name: str, phone: str, email: str | None = None, cpf: str | None = None
) -> dict:
    """Find existing Asaas customer by phone or create a new one."""
    base = settings.ASAAS_URL
    async with httpx.AsyncClient() as client:
        r = await client.get(
            f"{base}/v3/customers",
            headers=_headers(),
            params={"mobilePhone": phone},
        )
        data = r.json()
        if data.get("data"):
            return data["data"][0]

        payload: dict = {"name": name, "mobilePhone": phone}
        if email:
            payload["email"] = email
        if cpf:
            payload["cpfCnpj"] = cpf
        r = await client.post(f"{base}/v3/customers", headers=_headers(), json=payload)
        r.raise_for_status()
        return r.json()


# ── Subscriptions ──────────────────────────────────────────────────────────

async def create_subscription(
    customer_id: str,
    plan: str,  # "STARTER" | "PRO" | "BUSINESS"
    card_data: dict,
    card_holder_info: dict,
    trial_days: int | None = None,
) -> dict:
    """
    Cria assinatura recorrente mensal com cartão de crédito.

    card_data: {holderName, number, expiryMonth, expiryYear, ccv}
    card_holder_info: {name, email, cpfCnpj, phone, postalCode, addressNumber}
    trial_days: dias de trial gratuito; None = sem trial.
    """
    base = settings.ASAAS_URL
    effective_trial = trial_days if trial_days is not None else settings.TRIAL_DAYS

    next_due = date.today() + timedelta(days=effective_trial)

    payload: dict = {
        "customer": customer_id,
        "billingType": "CREDIT_CARD",
        "value": PLAN_VALUES[plan.upper()],
        "nextDueDate": next_due.isoformat(),
        "cycle": "MONTHLY",
        "description": PLAN_LABELS.get(plan.upper(), plan),
        "creditCard": card_data,
        "creditCardHolderInfo": card_holder_info,
    }

    if effective_trial > 0:
        trial_end = date.today() + timedelta(days=effective_trial)
        payload["trialEndDate"] = trial_end.isoformat()

    async with httpx.AsyncClient() as client:
        r = await client.post(
            f"{base}/v3/subscriptions", headers=_headers(), json=payload
        )
        r.raise_for_status()
        return r.json()


async def get_subscription(subscription_id: str) -> dict:
    base = settings.ASAAS_URL
    async with httpx.AsyncClient() as client:
        r = await client.get(
            f"{base}/v3/subscriptions/{subscription_id}", headers=_headers()
        )
        r.raise_for_status()
        return r.json()


async def cancel_subscription(subscription_id: str) -> dict:
    """Cancela a assinatura no Asaas."""
    base = settings.ASAAS_URL
    async with httpx.AsyncClient() as client:
        r = await client.delete(
            f"{base}/v3/subscriptions/{subscription_id}", headers=_headers()
        )
        r.raise_for_status()
        return r.json()


async def list_subscription_payments(subscription_id: str) -> list[dict]:
    """Lista cobranças geradas por uma assinatura."""
    base = settings.ASAAS_URL
    async with httpx.AsyncClient() as client:
        r = await client.get(
            f"{base}/v3/payments",
            headers=_headers(),
            params={"subscription": subscription_id},
        )
        r.raise_for_status()
        return r.json().get("data", [])


# ── One-off payments (legacy / agent tools) ───────────────────────────────

async def get_or_create_charge(
    customer_id: str,
    value: float,
    due_date: str,  # YYYY-MM-DD
    existing_charge_id: str | None = None,
) -> dict:
    """Retrieve an existing charge or create a new PIX charge (one-off)."""
    base = settings.ASAAS_URL
    async with httpx.AsyncClient() as client:
        if existing_charge_id:
            r = await client.get(
                f"{base}/v3/payments/{existing_charge_id}", headers=_headers()
            )
            if r.status_code == 200:
                return r.json()

        payload = {
            "customer": customer_id,
            "billingType": "PIX",
            "value": value,
            "dueDate": due_date,
        }
        r = await client.post(f"{base}/v3/payments", headers=_headers(), json=payload)
        r.raise_for_status()
        return r.json()


async def get_pix_qr_code(payment_id: str) -> dict:
    """Return PIX QR code data for a payment."""
    base = settings.ASAAS_URL
    async with httpx.AsyncClient() as client:
        r = await client.get(
            f"{base}/v3/payments/{payment_id}/pixQrCode", headers=_headers()
        )
        r.raise_for_status()
        return r.json()
