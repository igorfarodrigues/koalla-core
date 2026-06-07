# Boas Práticas de Desenvolvimento

## Princípios

- Fazer mudanças pequenas, coesas e com objetivo claro.
- Reutilizar padrões existentes antes de criar novas abstrações.
- Manter separação de camadas (router != service != model).
- Evitar duplicação de regra de negócio.

## Padrão de implementação

1. Definir/validar contrato de entrada (schema).
2. Implementar regra no service.
3. Expor rota no router apenas como orquestração HTTP.
4. Persistir com model e transações explícitas.

## Qualidade de código

- Nomes descritivos para variáveis, funções e módulos.
- Tratamento de erro explícito; sem `except` amplo para ocultar falhas.
- Não usar `TODO` sem contexto de decisão.
- Não acoplar lógica de negócio a detalhes de transporte HTTP.

## Banco de dados

- Preservar namespace no schema `koalla`.
- Mudanças de estrutura devem ser compatíveis com o fluxo atual.
- Evitar consultas sem filtro em tabelas potencialmente grandes.
