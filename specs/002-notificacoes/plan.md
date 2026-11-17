# Implementation Plan: Notificações de pedidos por e-mail

**Branch**: `002-notificacoes` (git: `feat/notificacoes`) | **Date**: 2026-11-16 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/002-notificacoes/spec.md`

## Summary

Um serviço novo, `notifications`, manda ao cliente um e-mail quando o pedido é
confirmado, enviado, entregue ou cancelado. Ele consome os fatos de pedido que o
`orders` já publica e mantém uma cópia do e-mail de cada cliente, alimentada por
dois fatos novos do `identity` e por uma carga inicial pela exportação dele. Cada
notificação é gravada antes de ser enviada, e um envio periódico, no molde do
relay do outbox, cuida da primeira tentativa e das seguintes. Os fatos de envio,
entrega e cancelamento passam a levar o `userId`, como a confirmação já leva. Em
desenvolvimento e nos testes, os e-mails caem no Mailpit. Ao final, o teste de
falha isolada da spec 003 (T108) fecha com as notificações paradas junto. As
decisões estão em [research.md](research.md).

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: as da spec 003 (Spring Boot 4.1.1, Spring AMQP 4.1.1,
Spring Data JPA, Flyway, springdoc-openapi 3.1.1, `spring-boot-starter-zipkin`),
mais `spring-boot-starter-mail`, na versão do BOM do Boot, conferida no classpath
antes do uso

**Storage**: MySQL 8.4, schema e usuário `notifications`; o `identity` ganha a
tabela `outbox_messages` no próprio schema

**Testing**: JUnit 5, Mockito, AssertJ, Testcontainers 2.0.5 (MySQL, RabbitMQ e
um Mailpit por `GenericContainer`), Awaitility, WireMock. Testes `*E2E` no perfil
`e2e`, com o Mailpit do Compose consultado pela API HTTP dele

**Target Platform**: containers Linux com Docker Compose, na máquina local e no
GitHub Actions (`ubuntu-latest`)

**Project Type**: serviços web; um serviço novo, `services/notifications`, e
mudanças no `identity`, no `orders`, no `contracts`, no gateway, no Compose e no `e2e`

**Performance Goals**: 99% das confirmações geram e-mail em até 1 minuto (SC-001)

**Constraints**: falha do e-mail não afeta pedido nenhum (FR-006, SC-003); nenhum
e-mail nos fatos de pedido (FR-007); fatos só crescem (regra 1 de
[events.md](../003-microsservicos/contracts/events.md)); memória do runner do CI
para mais uma JVM

**Scale/Scope**: uma aplicação nova (o gateway e sete serviços no total); dois
fatos novos e um campo novo em três fatos; quatro rotas novas

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio | Antes da pesquisa | Depois do desenho |
|---|---|---|
| I. Decisões têm motivo escrito | Passa: decisões vão para o research.md | Passa: R1 a R12 com motivo e alternativas |
| II. Menos código | Risco: serviço novo, retentativa e carga inicial | Passa: o serviço novo já está justificado na spec 003 (FR-002); o e-mail sai pelo `JavaMailSender`, a carga repete o molde do `sales` (R8), o envio repete o do outbox (R5), sem motor de template (R10) |
| III. Teste em toda mudança | Passa | Passa: os testes mais valiosos afirmam o que não acontece: fato repetido não manda segundo e-mail, status antigo não sai depois do novo, Mailpit parado não para pedido (R11) |
| IV. O domínio decide | Passa | Passa: a `Notification` decide tentativa, próximo horário e estado final; a `Contact` decide se um e-mail é mais novo |
| V. Fronteira pela visibilidade | Passa | Passa: pacotes `notification` e `contact`; só as services são públicas |
| VI. Erros com um formato | Passa | Passa: 403 e 503 da carga inicial em `ErrorResponse`, como no `sales` |
| VII. Segurança por construção | Risco: rota interna com e-mails de todos os clientes | Passa: exportação restrita a `ADMIN`, fora do gateway e da documentação, com a credencial do administrador repassada (R8) |
| Padrões de trabalho | Passa | Passa |
| Modelo flow-back | Passa | Passa: a spec foi atualizada antes do plano; o contrato de fatos da spec 003 é atualizado junto com o código |

## Project Structure

### Documentation (this feature)

```text
specs/002-notificacoes/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── events.md
│   ├── http.md
│   └── emails.md
├── checklists/
│   └── requirements.md
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

```text
contracts/
└── .../contracts/
    ├── order/            # userId em OrderShipped, OrderDelivered e OrderCancelled
    └── user/             # UserFacts, UserRegisteredEvent, UserEmailChangedEvent
services/
├── identity/             # outbox, fatos de usuário, GET /internal/users/export
├── orders/               # publica o userId nos três fatos
└── notifications/
    └── src/main/java/com/matheus/orderFlow/
        ├── NotificationsApplication.java
        ├── notification/ # Notification, envio periódico, listener dos fatos de
        │                 # pedido, GET /admin/notifications
        └── contact/      # Contact, listener dos fatos de usuário, carga inicial
gateway/                  # rota /admin/notifications/** e documentação
docker/
├── mysql/init/           # schema e usuário notifications
└── rabbitmq/users.sh     # usuários identity e notifications
docker-compose.yml        # notifications e mailpit
e2e/                      # NotificationsE2E, FailureIsolationE2E e ajustes
```

**Structure Decision**: mesmo layout dos outros serviços (R17 da spec 003). Dois
pacotes por funcionalidade: `contact` guarda o e-mail vigente e expõe
`ContactService` como porta; `notification` registra e envia, e consulta o
contato por essa porta na hora do envio.

## Etapas

| Etapa | O que muda |
|---|---|
| Base | `services/notifications` com schema, usuário no MySQL e no RabbitMQ, Mailpit e rota no gateway. Fatos de usuário no `contracts`; o `identity` publica pelo outbox; o `notifications` guarda os contatos. |
| US1 | E-mail de confirmação: registro único por pedido e status, envio periódico, tentativas com limite, falhas visíveis em `GET /admin/notifications`. |
| Carga | `GET /internal/users/export` no `identity` e `POST /admin/notifications/contacts/import` no `notifications` (FR-009). |
| US2 | `userId` nos fatos de envio e entrega; e-mails de envio e entrega; status antigo não sai depois do novo (FR-003). |
| US3 | `userId` no fato de cancelamento; e-mail de cancelamento. |
| Fim | `FailureIsolationE2E` (T108 da spec 003), notificações nos testes ponta a ponta de compra completa e de entrega dos fatos, README. |

## Riscos

1. **Memória do CI.** Mais uma JVM no job `e2e`. Mitigação: `-Xmx256m` como os
   outros e o Mailpit, que é leve.
2. **Contato que ainda não chegou.** Num ambiente novo, a carga inicial pode rodar
   depois dos primeiros pedidos. Mitigação: contato ausente conta como tentativa
   falha e só vira "sem destinatário" no fim das tentativas (R6).
3. **Volume antigo.** O MySQL não roda o script de schemas de novo, e a fila nova
   precisa do usuário novo do RabbitMQ. Mitigação: o passo do README para volume
   antigo ganha o schema `notifications`; os usuários do RabbitMQ já são criados a
   cada subida.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Envio periódico em vez de enviar no consumo | Guardar as tentativas e não segurar a fila quando o e-mail cai (FR-005, FR-006, FR-008) | Enviar no listener manda a mensagem para a DLQ ao esgotar as tentativas e perde o registro (R5) |
| Rota de exportação de usuários | Carga inicial sem ler o banco do `identity` (FR-009, FR-003 da spec 003) | A leitura direta viola a posse dos dados |
