"""Asaas billing API client — mirrors n8n subworkflow 06."""
import httpx
from app.config import get_settings

settings = get_settings()


def _headers() -> dict:
    return {
        "access_token": settings.ASAAS_API_KEY,
        "Content-Type": "application/json",
    }


async def get_or_create_customer(
    name: str, phone: str, cpf: str | None = None
) -> dict:
    """Find an existing Asaas customer by phone or create a new one."""
    base = settings.ASAAS_URL
    async with httpx.AsyncClient() as client:
        # Search by phone
        r = await client.get(
            f"{base}/v3/customers",
            headers=_headers(),
            params={"mobilePhone": phone},
        )
        data = r.json()
        if data.get("data"):
            return data["data"][0]

        # Create new customer
        payload: dict = {"name": name, "mobilePhone": phone}
        if cpf:
            payload["cpfCnpj"] = cpf
        r = await client.post(f"{base}/v3/customers", headers=_headers(), json=payload)
        r.raise_for_status()
        return r.json()


async def get_or_create_charge(
    customer_id: str,
    value: float,
    due_date: str,  # YYYY-MM-DD
    existing_charge_id: str | None = None,
) -> dict:
    """Retrieve an existing charge or create a new PIX charge."""
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
