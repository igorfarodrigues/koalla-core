from pydantic_settings import BaseSettings
from functools import lru_cache


class Settings(BaseSettings):
    # Database
    DATABASE_URL: str = "postgresql+asyncpg://koalla:koalla@localhost:5432/koalla"
    DB_SCHEMA: str = "koalla"

    # OpenAI
    OPENAI_API_KEY: str
    OPENAI_MODEL: str = "gpt-4o-mini"
    OPENAI_FORMATTING_MODEL: str = "gpt-4.1-mini"   

    # Chatwoot
    CHATWOOT_URL: str = "https://chatwoot.koalla.ai"
    CHATWOOT_API_TOKEN: str
    CHATWOOT_ACCOUNT_ID: int = 1

    # Segurança dos webhooks
    CHATWOOT_WEBHOOK_SECRET: str = ""

    # Asaas
    ASAAS_URL: str = "https://api-sandbox.asaas.com"
    ASAAS_API_KEY: str = ""
    ASAAS_WEBHOOK_TOKEN: str = ""   # token para validar webhooks do Asaas
    TRIAL_DAYS: int = 15            # dias de trial gratuito (promo)
    GRACE_HOURS: int = 48           # horas de carência após pagamento atrasado

    # Agent config
    #MESSAGE_QUEUE_WAIT_SECONDS: float = 0.1
    #Alterado o tempo de processo
    MESSAGE_QUEUE_WAIT_SECONDS: float = 2.0
    AGENT_MAX_ITERATIONS: int = 10
    MEMORY_WINDOW_LENGTH: int = 100

    # Billing (legacy — one-time charge via agent)
    CHARGE_VALUE: float = 500.0
    CHARGE_DURATION_MINUTES: int = 30

    # Planos (valores em R$)
    PLAN_STARTER_VALUE: float = 29.90
    PLAN_PRO_VALUE: float = 79.90
    PLAN_BUSINESS_VALUE: float = 99.90

    # Alert conversation (for human escalation notifications)
    ALERT_CONVERSATION_ID: str = ""

    # WhatsApp público do Koalla (usado no link de onboarding do signup)
    KOALLA_WA_NUMBER: str = "5531936185547"

    class Config:
        env_file = ".env"
        extra = "ignore"


@lru_cache()
def get_settings() -> Settings:
    return Settings()
