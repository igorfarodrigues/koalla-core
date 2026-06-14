# Estrutura de Pastas

## Raiz

- `app/`: código principal da API
- `migrations/`: schema SQL inicial
- `docker-compose.yml`: ambiente local (API + Postgres)
- `Dockerfile`: build e runtime
- `entrypoint.sh`: bootstrap/migração e subida da API
- `requirements.txt`: dependências Python

## `app/`

- `main.py`: criação da aplicação FastAPI e lifespan
- `config.py`: settings (env vars)
- `database.py`: engine async e sessão
- `routers/`: endpoints HTTP
- `schemas/`: modelos de entrada/saída
- `services/`: lógica de negócio e integrações
- `models/`: ORM SQLAlchemy
- `tools/`: ferramentas do agente de IA

## Convenção de responsabilidade

- Router: valida request e retorna response.
- Service: executa regra de negócio.
- Model: representa persistência.
- Schema: contrato de dados.
