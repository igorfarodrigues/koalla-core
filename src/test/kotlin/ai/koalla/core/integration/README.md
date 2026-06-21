# Integration Tests

Esta pasta contém os testes de integração do koalla-core.

## Estrutura

```
integration/
  ├── IntegrationTestBase.kt     # Base class para testes de integração
  ├── README.md                  # Este arquivo
  └── *IntegrationTest.kt        # Testes de integração
```

## Executando

```bash
# Executar apenas testes unitários (exclui @Tag("integration"))
./gradlew test

# Executar apenas testes de integração
./gradlew integrationTest

# Executar todos os testes
./gradlew test integrationTest
```

## Criando um novo teste de integração

1. Estenda `IntegrationTestBase`
2. Adicione a anotação `@Tag("integration")` na classe
3. Use `@Autowired` para injetar dependências

```kotlin
@Tag("integration")
class MyControllerIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var myRepository: MyRepository

    @Test
    fun `should do something`() {
        // Test implementation
    }
}
```

## Configuração

Os testes de integração usam:
- **Profile**: `test` (application-test.yml)
- **Database**: H2 in-memory
- **WireMock**: Para mock de APIs externas (Chatwoot, Asaas)

## Notas

- Testes de integração são mais lentos que testes unitários
- Use `@BeforeEach` para limpar dados entre testes
- O WireMock está disponível via `wireMockServer` na base class

