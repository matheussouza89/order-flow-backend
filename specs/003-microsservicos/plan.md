# Implementation Plan: Separação em microsserviços

**Branch**: `003-microsservicos` (git: `spec/microsservicos`) | **Date**: 2026-10-22 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/003-microsservicos/spec.md`

## Summary

Separar o OrderFlow em serviços, um por capacidade, sem mudar a API para o cliente.
A primeira etapa monta a base: Maven multi-módulo, entrada única com Spring Cloud
Gateway, credencial RS256 com chaves públicas, rastreamento com Zipkin, fatos com
tipo lógico versionado e o Compose que sobe tudo. As etapas seguintes tiram um
pacote do monólito por vez (relatórios, pagamentos, catálogo, carrinho,
identidade) até o que sobra virar o serviço de pedidos. Um teste de carga mede o sistema
depois da primeira etapa e no fim, para registrar o custo da separação. As
decisões estão em
[research.md](research.md).

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.1.1; Spring Cloud 2025.1.3, com Gateway
Server WebMVC 5.0.3; Spring Security resource server; Spring AMQP 4.1.1; Spring
Data JPA, Redis e MongoDB; Flyway; Resilience4j 2.3.0; springdoc-openapi 3.1.1;
`spring-boot-starter-zipkin` (Micrometer Tracing com Brave)

**Storage**: MySQL 8.4 (um servidor, um schema e um usuário por serviço); Redis 7
(um usuário de ACL por serviço); MongoDB 7 (`sales`); RabbitMQ 4 (fatos)

**Testing**: JUnit 5, Mockito, AssertJ, Testcontainers 2.0.5, Awaitility,
WireMock. Surefire roda `*Test`, Failsafe roda `*IT`. Testes `*E2E` novos, no
perfil Maven `e2e`, sobem o Compose pelo Testcontainers. Teste de carga com
Gatling 3.16.0 no módulo `load` (R18).

**Target Platform**: containers Linux com Docker Compose, na máquina local e no
GitHub Actions (`ubuntu-latest`)

**Project Type**: serviços web: um gateway e sete serviços, com as bibliotecas
internas `platform-*` e `contracts` e os módulos de teste `e2e` e `load`

**Performance Goals**: com 50 clientes simultâneos por 5 minutos, p95 da
finalização até 1 s, p95 da vitrine até 300 ms e menos de 1% de falhas (SC-013,
SC-014); no fim, p95 da finalização até o dobro da linha de base (SC-015). Falha
do checkout em até 5 s (SC-003).

**Constraints**: API pública sem mudança (FR-001); tudo sobe por um comando
(FR-015); uma capacidade por etapa, com o sistema inteiro funcionando entre elas
(FR-013); nenhuma chave privada versionada; memória do runner do CI para oito JVMs
e a infraestrutura

**Scale/Scope**: 8 aplicações (gateway, `identity`, `catalog`, `cart`, `orders`,
`payment`, `sales` e `notifications`, esta pela spec 002); 365 testes de hoje
(230 unitários e 135 de integração, com o teste de contexto contado como
integração) redistribuídos; 7 etapas

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio | Antes da pesquisa | Depois do desenho |
|---|---|---|
| I. Decisões têm motivo escrito | Passa: decisões vão para o research.md (constituição 1.1.0) | Passa: R0 a R18 com motivo e alternativas |
| II. Menos código | Violação justificada: oito aplicações em vez de uma | Mantida e justificada em Complexity Tracking; a biblioteca resolve cliente HTTP, circuit breaker, gateway e rastreamento (R3, R6, R11) |
| III. Teste em toda mudança | Passa: os testes acompanham o código | Passa: testes de contrato dos fatos e módulo `e2e` para os critérios entre serviços (R15) |
| IV. O domínio decide | Passa: entidades mudam de módulo sem mudar de regra | Passa |
| V. Fronteira pela visibilidade | Passa: a fronteira passa a ser também física | Passa: o `platform` não conhece domínio; exceções de funcionalidade ficam no serviço, e o mapa de tipos dos fatos vem de cada serviço (R2, R9, R17) |
| VI. Erros com um formato | Risco: a entrada única tem erros próprios | Passa: 503 e 504 em `ErrorResponse` (R4) |
| VII. Segurança por construção | Risco: credencial compartilhada entre serviços | Passa: RS256, só o emissor assina, chave privada fora do repositório, credencial do cliente repassada (R5, R6) |
| Padrões de trabalho | Passa: `verify` verde, README e contagem de testes a cada etapa | Passa: documentação única da API no gateway (R13) |
| Modelo flow-back | Passa | Passa |

## Project Structure

### Documentation (this feature)

```text
specs/003-microsservicos/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── gateway-routes.md
│   ├── events.md
│   └── http-internal.md
├── checklists/
│   └── requirements.md
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

```text
pom.xml                   # agregador e parent de todos os módulos
platform/                 # bibliotecas sem domínio, cada serviço usa só as suas
├── platform-web/         # ErrorResponse, handler de erros, PageResponse
├── platform-security/    # resource server, rotas públicas por propriedade,
│                         # repasse da credencial nas chamadas HTTP
├── platform-messaging/   # exchange, DLX e DLQ, conversor com ids lógicos
├── platform-outbox/      # outbox com traceparent
└── platform-test/        # par de chaves e tokens para os testes
contracts/                # fatos publicados e ids lógicos de tipo
gateway/                  # entrada única e página única de documentação
monolith/                 # a aplicação de hoje; perde um pacote por etapa
services/
├── identity/             # US6
├── catalog/              # US4
├── cart/                 # US5
├── orders/               # US7: o monolith renomeado
├── payment/              # US3
├── sales/                # US2
└── notifications/        # spec 002
e2e/                      # testes *E2E contra o Compose
load/                     # teste de carga com Gatling
docker/
└── mysql/init/           # schemas e usuários por serviço
Dockerfile                # ARG MODULE
docker-compose.yml
```

Cada serviço tem o layout Maven de sempre:
`src/main/java/com/matheus/orderFlow/<funcionalidade>/`,
`src/main/resources/db/migration/` (quando usa MySQL) e os testes em
`src/test/java/` no mesmo pacote.

**Structure Decision**: Maven multi-módulo num repositório (R2). O `platform` é
dividido em módulos pequenos para que cada serviço dependa só do que usa, sem
configuração condicional, salvo o `JwtDecoder` (R2): o gateway não carrega o resource server, e um serviço sem
JPA não carrega o outbox. Os serviços mantêm o pacote da funcionalidade de hoje, e a
classe de aplicação de cada um fica em `com.matheus.orderFlow`, para que o
component scan alcance os pacotes do `platform` (R17). O `monolith` existe só
durante a migração e vira `services/orders` na última etapa (R16).

## Etapas

Cada user story é uma etapa, entregue e testada sozinha (FR-013).

| Etapa | O que muda |
|---|---|
| Base | Multi-módulo com `monolith`, `platform`, `contracts`, `gateway` e `e2e`. `shared/` vira `platform`, sem domínio. Tipo lógico nos fatos. `traceparent` no outbox. Zipkin. `Dockerfile` com `ARG MODULE` e job `e2e` no CI. |
| US1 | RS256 e JWKS no `monolith`. Gateway com os prefixos da API apontando para o `monolith`, timeout de 4,5 s, erros 503 e 504, página única de documentação. Compose com o gateway na frente. Módulo `load` e linha de base do teste de carga. |
| 002 | `services/notifications`, pela spec própria, de preferência logo depois da US1. |
| US2 | `services/sales`. O `monolith` ganha `GET /internal/orders/export`; o `sales` ganha `POST /admin/sales/rebuild`. O pacote `sales` sai do `monolith`. |
| US3 | `services/payment`. `RENAME TABLE` de `payments`. O pacote `payment` sai do `monolith`. |
| US4 | `services/catalog`. `RENAME TABLE` de `products`. Carrinho e pedidos, ainda no `monolith`, passam a chamar o `catalog` por HTTP. |
| US5 | `services/cart`. `POST /orders` aceita `Idempotency-Key`. O `cart` chama o `catalog` e o `monolith` por HTTP. A finalização tem prazo de 4 s (R6). |
| US6 | `services/identity`. `RENAME TABLE` de `users`; refresh tokens com usuário de ACL próprio. O JWKS sai do `monolith`. |
| US7 | O `monolith` vira `services/orders`. `RENAME TABLE` de `orders`, `order_items` e `outbox_messages`. O gateway deixa de apontar para o `monolith`. |
| Fim | Medição final de carga com o mesmo cenário, comparada com a linha de base (SC-015). |

## Riscos

1. **Memória do CI.** Oito JVMs e a infraestrutura no mesmo runner. Mitigação:
   limite de heap por serviço no Compose e o `e2e` num job separado.
2. **Rastro no outbox.** A restauração do contexto na publicação é o ponto mais
   fácil de errar; o SC-010 e o cenário 8 do [quickstart](quickstart.md) cobrem.
3. **Nomes de propriedades.** Gateway, clientes HTTP do Boot 4 e a classe de
   Compose do Testcontainers mudaram entre versões; conferir no classpath antes de
   usar, como pede a constituição.
4. **Parada nas etapas com `RENAME TABLE`.** O monólito e o serviço novo não podem
   escrever na tabela ao mesmo tempo; a etapa para o sistema por alguns minutos,
   como as premissas aceitam. O script de cada etapa fica em
   `docker/mysql/migrations/` e tem teste próprio.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Oito aplicações em vez de uma (princípio II) | Objetivo de portfólio e FR-002: cada capacidade implantada sozinha | O monólito modular não implanta capacidades separadas (R0) |
| Módulos compartilhados `platform` e `contracts` | `ErrorResponse`, segurança, outbox e fatos precisam ser iguais em todos os serviços (FR-001, FR-005, princípio VI) | Copiar em cada serviço deixa as cópias divergirem |
| Chave de idempotência no checkout | A finalização atravessa dois serviços (FR-009) | Carrinho e pedidos no mesmo serviço foi descartado na spec |
| Rotas novas de exportação e reconstrução | Reconstruir os relatórios sem ler o banco dos pedidos (FR-010, FR-003) | A leitura direta de hoje viola o FR-003 |
| Módulo `e2e` e job de CI separado | Isolamento de falha e entrada única só se provam com os serviços juntos | Testes por serviço não provam os SCs de isolamento |
