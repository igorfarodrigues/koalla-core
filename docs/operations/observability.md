# Observabilidade

## Logs

### Formato

Os logs seguem o padrão estruturado do Logback:

```
2024-01-15 10:30:45.123 INFO  [main] a.k.c.KoallaApplication : Started KoallaApplication
2024-01-15 10:30:46.456 DEBUG [http-nio-8080-exec-1] a.k.c.s.MessagePipelineService : Processing webhook for wa_id=+5531999999999
```

### Níveis

| Nível | Uso |
|-------|-----|
| ERROR | Erros que requerem atenção imediata |
| WARN | Situações anormais mas recuperáveis |
| INFO | Eventos importantes do fluxo normal |
| DEBUG | Detalhes para troubleshooting |

### Configuração

`application.yml`:
```yaml
logging:
  level:
    root: INFO
    ai.koalla: DEBUG
    org.springframework.ai: DEBUG
```

### Logs Importantes

```bash
# Webhook recebido
grep "Processing webhook" logs/app.log

# Execução do agente
grep "Agent execution" logs/app.log

# Erros de transcrição
grep "transcription failed" logs/app.log

# Erros de API externa
grep "Failed to" logs/app.log
```

## Métricas

### Spring Actuator

Endpoints disponíveis em `/actuator/`:

| Endpoint | Descrição |
|----------|-----------|
| `/health` | Status de saúde |
| `/info` | Informações da aplicação |
| `/metrics` | Métricas JVM e custom |
| `/prometheus` | Métricas em formato Prometheus |

### Métricas Chave

```
# JVM
jvm_memory_used_bytes
jvm_threads_live_threads
jvm_gc_pause_seconds_count

# HTTP
http_server_requests_seconds_count
http_server_requests_seconds_sum

# Database
hikaricp_connections_active
hikaricp_connections_pending
```

### Custom Metrics

Métricas customizadas implementadas em `observability/PipelineMetrics.kt`:

```kotlin
@Component
class PipelineMetrics(private val registry: MeterRegistry) {

    val agentTimer: Timer = Timer.builder("koalla.agent.duration")
        .description("End-to-end agent + tool execution duration")
        .register(registry)

    fun messageProcessed(type: String = "text") {
        Counter.builder("koalla.messages.processed")
            .description("Messages delivered to the AI agent")
            .tag("type", type)
            .register(registry)
            .increment()
    }

    fun messageBlocked(reason: String) {
        Counter.builder("koalla.messages.blocked")
            .description("Messages skipped before reaching the agent")
            .tag("reason", reason)
            .register(registry)
            .increment()
    }

    fun pipelineError() {
        Counter.builder("koalla.pipeline.errors")
            .description("Unhandled exceptions in the message pipeline")
            .register(registry)
            .increment()
    }
}
```

| Métrica | Tipo | Tags | Descrição |
|---------|------|------|-----------|
| `koalla.messages.processed` | Counter | type=text\|audio | Mensagens entregues ao agente |
| `koalla.messages.blocked` | Counter | reason=label\|inactive\|unregistered | Mensagens bloqueadas antes do agente |
| `koalla.pipeline.errors` | Counter | - | Exceções não tratadas no pipeline |
| `koalla.agent.duration` | Timer | - | Duração end-to-end do agente + tools |

## Alertas

### Alertas Recomendados

| Alerta | Condição | Severidade |
|--------|----------|------------|
| API Down | health != UP por 5min | Critical |
| Alta Latência | p99 > 10s | Warning |
| Erros Frequentes | error rate > 5% | Warning |
| DB Connections | active > 80% pool | Warning |
| Memory | used > 90% | Warning |
| Locks Travados | locks > 5min | Warning |

### Prometheus AlertManager (Exemplo)

```yaml
groups:
  - name: koalla-alerts
    rules:
      - alert: KoallaAPIDown
        expr: up{job="koalla-core"} == 0
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "Koalla API is down"
          
      - alert: KoallaHighLatency
        expr: histogram_quantile(0.99, rate(http_server_requests_seconds_bucket[5m])) > 10
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High latency detected"
```

## Dashboards

### Grafana Dashboard (JSON)

Principais painéis:
1. **Health Overview**: Status dos componentes
2. **Request Rate**: Requests/segundo por endpoint
3. **Response Time**: Latência p50, p95, p99
4. **Error Rate**: Taxa de erros 4xx/5xx
5. **Agent Metrics**: Chamadas do agente e tools
6. **Database**: Conexões, queries, latência

### Queries Úteis

```promql
# Taxa de requests por minuto
rate(http_server_requests_seconds_count[1m])

# Latência média
rate(http_server_requests_seconds_sum[5m]) / rate(http_server_requests_seconds_count[5m])

# Taxa de erros
sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) / sum(rate(http_server_requests_seconds_count[5m]))
```

## Tracing (Futuro)

### OpenTelemetry

```kotlin
// Configuração futura para tracing distribuído
@Configuration
class TracingConfig {
    @Bean
    fun tracer(): Tracer {
        return OpenTelemetry.noop().getTracer("koalla-core")
    }
}
```

Spans importantes:
- `webhook.process` - Pipeline completo
- `agent.run` - Execução do agente
- `tool.execute` - Execução de cada tool
- `chatwoot.api` - Chamadas ao Chatwoot
- `openai.api` - Chamadas à OpenAI

