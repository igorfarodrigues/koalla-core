# CI/CD Pipelines

## Overview

O projeto utiliza GitHub Actions para CI e Coolify para deploy.

## Build Local

```bash
# Build
./gradlew build

# Run tests
./gradlew test

# Run application
./gradlew bootRun

# Build Docker image
docker build -t koalla-core .

# Run with Docker Compose
docker-compose up -d
```

## GitHub Actions

### CI Pipeline (`.github/workflows/ci.yml`)

```yaml
name: CI

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

jobs:
  build:
    runs-on: ubuntu-latest
    
    steps:
      - uses: actions/checkout@v4
      
      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: gradle
      
      - name: Grant execute permission for gradlew
        run: chmod +x gradlew
      
      - name: Build with Gradle
        run: ./gradlew build
      
      - name: Run tests
        run: ./gradlew test
      
      - name: Upload test results
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: test-results
          path: build/reports/tests/
```

### Stages

| Stage | Descrição | Trigger |
|-------|-----------|---------|
| **Lint** | Validação de código (ktlint) | PR, Push |
| **Test** | Testes unitários e integração | PR, Push |
| **Build** | Compilação e JAR | PR, Push |
| **Docker** | Build da imagem Docker | Push main |
| **Deploy** | Deploy para Coolify | Push main |

## Docker

### Dockerfile

```dockerfile
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY . .
RUN ./gradlew build -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Docker Compose (Desenvolvimento)

```yaml
services:
  api:
    build: .
    ports:
      - "8080:8080"
    environment:
      - SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/koalla
      - SPRING_DATASOURCE_USERNAME=koalla
      - SPRING_DATASOURCE_PASSWORD=koalla
    depends_on:
      - db
  
  db:
    image: postgres:15-alpine
    environment:
      - POSTGRES_DB=koalla
      - POSTGRES_USER=koalla
      - POSTGRES_PASSWORD=koalla
    volumes:
      - pgdata:/var/lib/postgresql/data
      - ./migrations:/docker-entrypoint-initdb.d

volumes:
  pgdata:
```

## Deploy - Coolify

### Configuração

1. **Repository**: Conectar ao repositório GitHub
2. **Branch**: `main`
3. **Build Pack**: Dockerfile
4. **Port**: 8080

### Environment Variables

```
SPRING_PROFILES_ACTIVE=prod
SPRING_DATASOURCE_URL=jdbc:postgresql://...
SPRING_DATASOURCE_USERNAME=...
SPRING_DATASOURCE_PASSWORD=...
SPRING_AI_OPENAI_API_KEY=...
CHATWOOT_BASE_URL=...
CHATWOOT_API_KEY=...
KOALLA_ALERT_CONVERSATION_ID=...
```

### Health Check

```
GET /actuator/health
```

Retorna:
```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "diskSpace": { "status": "UP" }
  }
}
```

## Rollback

Em caso de falha no deploy:

1. Acessar Coolify dashboard
2. Ir em Deployments
3. Selecionar versão anterior
4. Clicar em "Redeploy"

Ou via API:
```bash
curl -X POST "https://coolify.example.com/api/v1/deploy" \
  -H "Authorization: Bearer $COOLIFY_TOKEN" \
  -d '{"uuid": "app-uuid", "commit": "previous-commit-sha"}'
```

## Monitoramento

### Logs
```bash
# Coolify
coolify logs -f koalla-core

# Docker local
docker-compose logs -f api
```

### Métricas (Spring Actuator)
```
GET /actuator/metrics
GET /actuator/prometheus
```

