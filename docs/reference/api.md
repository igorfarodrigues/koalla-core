# API Reference

## Base URL

```
Production: https://api.koalla.ai
Development: http://localhost:8080
```

## Documentação Interativa (Swagger UI)

A API possui documentação interativa via OpenAPI/Swagger:

| Recurso | URL |
|---------|-----|
| **Swagger UI** | `/swagger-ui.html` |
| **OpenAPI JSON** | `/v3/api-docs` |
| **OpenAPI YAML** | `/v3/api-docs.yaml` |

Acesse `http://localhost:8080/swagger-ui.html` para explorar e testar os endpoints.

## Authentication

A API usa autenticação básica via header para endpoints administrativos.

```http
Authorization: Basic base64(username:password)
```

## Endpoints

### Health

#### GET /actuator/health

Verifica status da aplicação.

**Response 200:**
```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "diskSpace": { "status": "UP" }
  }
}
```

---

### Webhooks

#### POST /webhook/chatwoot

Recebe eventos do Chatwoot (mensagens WhatsApp).

**Headers:**
```http
Content-Type: application/json
```

**Request Body:**
```json
{
  "event": "message_created",
  "id": 123456,
  "content": "Almoço 45",
  "message_type": "incoming",
  "account": {
    "id": 1
  },
  "conversation": {
    "id": 789,
    "labels": [],
    "meta": {
      "sender": {
        "phone_number": "+5531999999999"
      }
    },
    "contact_inbox": {
      "contact_id": 456
    },
    "custom_attributes": {},
    "messages": []
  },
  "sender": {
    "name": "João Silva"
  },
  "attachments": []
}
```

**Response 200:**
```json
{
  "status": "accepted"
}
```

---

#### POST /webhook/asaas

Recebe eventos de billing do Asaas.

**Headers:**
```http
Content-Type: application/json
asaas-access-token: {webhook_token}
```

**Eventos tratados:**
- `PAYMENT_CONFIRMED` / `PAYMENT_RECEIVED` → Ativa usuário
- `PAYMENT_OVERDUE` / `PAYMENT_CREDIT_CARD_CAPTURE_REFUSED` → Marca atraso
- `SUBSCRIPTION_DELETED` / `SUBSCRIPTION_INACTIVATED` → Cancela assinatura

**Request Body:**
```json
{
  "id": "evt_123456789",
  "event": "PAYMENT_CONFIRMED",
  "payment": {
    "id": "pay_123456789",
    "subscription": "sub_123456789"
  }
}
```

**Response 200:**
```json
{
  "status": "ok",
  "event": "PAYMENT_CONFIRMED"
}
```

**Response 401:**
```json
{
  "error": "Invalid webhook token"
}
```

---

#### POST /webhook/cancel-subscription/{waId}

Cancela a assinatura de um usuário.

**Headers:**
```http
X-Koalla-Secret: {secret}
```

**Response 200:**
```json
{
  "success": true,
  "subscriptionId": "sub_123456789"
}
```

---

### Users

#### GET /api/users/{waId}

Busca usuário por WhatsApp ID.

**Parameters:**
| Nome | Tipo | Descrição |
|------|------|-----------|
| waId | string | WhatsApp ID (path) |

**Response 200:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "waId": "+5531999999999",
  "name": "João Silva",
  "email": "joao@email.com",
  "isActive": true,
  "trialEndsAt": "2024-01-30T00:00:00Z",
  "createdAt": "2024-01-15T10:30:00Z"
}
```

**Response 404:**
```json
{
  "error": "User not found"
}
```

#### PATCH /api/users/{userId}/deactivate

Desativa um usuário.

**Response 200:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "isActive": false
}
```

---

### Transactions

#### GET /api/transactions/user/{userId}

Lista transações de um usuário.

**Parameters:**
| Nome | Tipo | Descrição |
|------|------|-----------|
| userId | UUID | ID do usuário (path) |
| limit | int | Máximo de resultados (query, default: 50) |

**Response 200:**
```json
[
  {
    "id": "660e8400-e29b-41d4-a716-446655440001",
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "description": "Almoço",
    "amount": 4500,
    "movement": "CASH_OUT",
    "categoryId": "770e8400-e29b-41d4-a716-446655440002",
    "entityType": "PF",
    "source": "whatsapp",
    "occurredAt": "2024-01-15T12:30:00Z",
    "createdAt": "2024-01-15T12:30:05Z"
  }
]
```

#### POST /api/transactions

Cria uma nova transação.

**Request Body:**
```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "description": "Uber",
  "amount": 3400,
  "movement": "CASH_OUT",
  "categoryId": "770e8400-e29b-41d4-a716-446655440003",
  "entityType": "PF",
  "source": "api",
  "occurredAt": "2024-01-15T14:00:00Z"
}
```

**Response 201:**
```json
{
  "id": "660e8400-e29b-41d4-a716-446655440002",
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "description": "Uber",
  "amount": 3400,
  "movement": "CASH_OUT",
  "categoryId": "770e8400-e29b-41d4-a716-446655440003",
  "createdAt": "2024-01-15T14:00:05Z"
}
```

#### DELETE /api/transactions/{id}

Remove uma transação.

**Response 204:** No content

**Response 404:**
```json
{
  "error": "Transaction not found"
}
```

#### GET /api/transactions/user/{userId}/summary

Resumo mensal de transações.

**Parameters:**
| Nome | Tipo | Descrição |
|------|------|-----------|
| userId | UUID | ID do usuário (path) |
| year | int | Ano (query) |
| month | int | Mês (query) |

**Response 200:**
```json
{
  "totalCashIn": 500000,
  "totalCashOut": 234500,
  "balance": 265500,
  "byCategory": {
    "Alimentacao": 45000,
    "Transporte": 89500,
    "Mercado": 100000
  }
}
```

---

### Auth

#### POST /auth/signup

Cadastro de novo usuário com trial.

**Request Body:**
```json
{
  "waId": "+5531999999999",
  "fullName": "João Silva",
  "email": "joao@email.com",
  "plan": "monthly",
  "card": {
    "holderName": "JOAO SILVA",
    "number": "4111111111111111",
    "expiryMonth": "12",
    "expiryYear": "2025",
    "ccv": "123"
  },
  "cardHolderInfo": {
    "name": "João Silva",
    "email": "joao@email.com",
    "cpfCnpj": "12345678901",
    "postalCode": "30130000",
    "addressNumber": "123",
    "phone": "31999999999"
  }
}
```

**Response 200 (sucesso):**
```json
{
  "subscriptionId": "sub_123456789",
  "trialEndDate": "2024-02-14",
  "plan": "monthly",
  "waLink": "https://wa.me/5531988888888?text=Oi%20Koalla!%20Acabei%20de%20me%20cadastrar%20%F0%9F%90%A8"
}
```

**Response 200 (já cadastrado):**
```json
{
  "success": false,
  "detail": "already_registered",
  "waLink": "https://wa.me/5531988888888?text=Oi+Koalla!"
}
```

**Response 422:**
```json
{
  "error": "Failed to process payment"
}
```

---

#### GET /auth/status/{waId}

Verifica status de cadastro do usuário.

**Parameters:**
| Nome | Tipo | Descrição |
|------|------|-----------|
| waId | string | WhatsApp ID (path) |

**Response 200:**
```json
{
  "waId": "+5531999999999",
  "isActive": true,
  "planType": "MONTHLY"
}
```

**Response 404:**
```json
{
  "error": "User not found"
}
```

---

## Error Responses

Todos os erros seguem o formato:

```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/transactions"
}
```

### HTTP Status Codes

| Code | Descrição |
|------|-----------|
| 200 | OK |
| 201 | Created |
| 204 | No Content |
| 400 | Bad Request |
| 401 | Unauthorized |
| 404 | Not Found |
| 500 | Internal Server Error |

---

## Rate Limiting

| Endpoint | Limite |
|----------|--------|
| /webhook/* | 100 req/min |
| /api/* | 60 req/min |

Headers de resposta:
```http
X-RateLimit-Limit: 60
X-RateLimit-Remaining: 45
X-RateLimit-Reset: 1705321800
```

