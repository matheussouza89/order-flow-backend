---

description: "Tarefas da implementação das notificações de pedidos por e-mail"
---

# Tasks: Notificações de pedidos por e-mail

**Input**: Design documents from `/specs/002-notificacoes/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Tests**: obrigatórios pela constituição (princípio III). Em cada fase, os testes
vêm antes do código e devem falhar antes da implementação.

**Organization**: uma fase por user story, depois da base comum. A carga inicial
(FR-009) tem fase própria, entre a US1 e a US2, porque nenhum cliente antigo
recebe e-mail sem ela.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: pode rodar em paralelo (arquivos diferentes, sem dependência pendente)
- **[Story]**: a user story da tarefa (US1, US2, US3)

## Path Conventions

Pacotes Java em `com.matheus.orderFlow`. O serviço novo fica em
`services/notifications/`, com `src/main/java/com/matheus/orderFlow/notification/`,
`.../contact/` e os testes no mesmo pacote; os testes de integração ficam em
`.../integration/`, como nos outros serviços.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: o serviço novo existe, sobe no Compose e é alcançável pela entrada única

- [X] T001 Criar o módulo `services/notifications` com `pom.xml` no molde de `services/payment/pom.xml` (`platform-web`, `platform-security`, `platform-messaging`, `contracts`, Data JPA, Flyway MySQL, AMQP, actuator, zipkin, springdoc; nos testes, `platform-test`, Testcontainers MySQL e RabbitMQ, Awaitility, WireMock) mais `spring-boot-starter-mail`, conferindo no classpath o pacote do `MailSender` e o nome das propriedades `spring.mail.*` no Boot 4.1.1; criar `services/notifications/src/main/java/com/matheus/orderFlow/NotificationsApplication.java` com `@EnableScheduling`; registrar `services/notifications` em `<modules>` do `pom.xml` raiz
- [X] T002 Criar `services/notifications/src/main/resources/application.yml` no molde de `services/payment/src/main/resources/application.yml`: datasource `jdbc:mysql://localhost:3308/notifications` com usuário `notifications`, RabbitMQ com usuário `notifications`, JWK, tracing; `spring.mail.host: ${MAIL_HOST:localhost}`, `spring.mail.port: ${MAIL_PORT:1025}`, `mail.smtp.connectiontimeout: 2000` e `mail.smtp.timeout: 5000` (R6); `orderflow.notifications.mail.from: ${MAIL_FROM:nao-responda@orderflow.local}`; `orderflow.notifications.sender.poll-interval: ${NOTIFICATIONS_POLL_INTERVAL:5000}`
- [X] T003 [P] Em `docker/mysql/init/01-schemas.sql`, criar o schema `notifications`, o usuário `notifications` e o `GRANT` só nesse schema
- [X] T004 [P] Em `docker/rabbitmq/users.sh`, criar o usuário `identity` (configura e escreve só `orderflow.events`, a DLX e a DLQ, como o `orders`) e o usuário `notifications` (configura e lê só as filas `order.status.notifications` e `user.contacts.notifications`, além da DLX e da DLQ, no molde do `payment`), com `RABBITMQ_IDENTITY_PASSWORD` e `RABBITMQ_NOTIFICATIONS_PASSWORD` passadas ao container `rabbitmq-users`
- [X] T005 Em `docker-compose.yml`: serviço `mailpit` com a imagem `axllent/mailpit` numa versão fixa, conferida antes, e a porta 8025 publicada; serviço `notifications` no molde do `payment` (datasource, RabbitMQ com `RABBITMQ_NOTIFICATIONS_PASSWORD`, `JWK_SET_URI`, `IDENTITY_URL`, `MAIL_HOST: mailpit`, `MAIL_PORT: 1025`, Zipkin, `-Xmx256m`, healthcheck, `depends_on` com `mysql` saudável, `rabbitmq-users` concluído e `mailpit`); no `identity`, `SPRING_RABBITMQ_HOST`, usuário `identity` com `RABBITMQ_IDENTITY_PASSWORD` e `depends_on` no `rabbitmq-users`; no `gateway`, `NOTIFICATIONS_URL` e `depends_on` no `notifications`
- [X] T006 [P] Teste primeiro em `gateway/src/test/java/com/matheus/orderFlow/gateway/GatewayRoutingIT.java` (`/admin/notifications` e `/admin/notifications/contacts/import` chegam ao `notifications`, e a nenhum outro) e em `gateway/src/test/java/com/matheus/orderFlow/gateway/DocumentationIT.java` (`/v3/api-docs/notifications`); depois, em `gateway/src/main/resources/application.yml`, as rotas `notifications` (`Path=/admin/notifications/**`) e `notifications-docs`, e a entrada `notifications` em `springdoc.swagger-ui.urls`

**Checkpoint**: `docker compose up --build` sobe o `notifications` e o Mailpit, saudáveis

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: o `identity` publica os fatos de usuário e o `notifications` guarda o e-mail vigente de cada cliente (FR-007, R3, R4)

**⚠️ CRITICAL**: nenhuma user story manda e-mail sem o contato

### Tests

- [X] T007 [P] Criar `contracts/src/test/java/com/matheus/orderFlow/contracts/user/UserFactFormatTest.java`, no molde de `contracts/.../order/FactFormatTest.java`: ids `user.registered.v1` e `user.email-changed.v1`, chaves `user.registered` e `user.email-changed`, e os campos `userId`, `email` e `occurredAt`, nessa ordem
- [X] T008 [P] Criar `services/identity/src/test/java/com/matheus/orderFlow/integration/UserFactsIT.java`, com RabbitMQ acrescentado em `services/identity/src/test/java/com/matheus/orderFlow/TestcontainersConfiguration.java`: o cadastro grava `user.registered` no outbox com `occurredAt` igual ao `updated_at` do usuário; a troca de e-mail grava `user.email-changed` com o e-mail novo; um cadastro recusado (e-mail repetido) e uma troca recusada não gravam nada; o relay publica na exchange `orderflow.events` com a chave certa
- [X] T009 [P] Criar `services/notifications/src/test/java/com/matheus/orderFlow/contact/ContactTest.java`: `email` obrigatório, "até 150 caracteres"; `replaceIfNewer` troca o e-mail só com instante maior que o guardado; com instante igual ou menor, o contato não muda
- [X] T010 [P] Criar `services/notifications/src/test/java/com/matheus/orderFlow/integration/AbstractIntegrationTest.java` e `TestcontainersConfiguration.java` (MySQL, RabbitMQ e Mailpit por `GenericContainer`, com a API HTTP do Mailpit exposta aos testes), no molde de `services/payment/src/test/java/`; e `ContactIT.java`: `user.registered` e `user.email-changed` entregues pelo RabbitMQ gravam o contato; uma troca entregue fora de ordem não volta o e-mail antigo; o mesmo fato duas vezes não altera nada

### Implementation

- [X] T011 [P] Criar `contracts/src/main/java/com/matheus/orderFlow/contracts/user/UserRegisteredEvent.java` e `UserEmailChangedEvent.java` (`UUID userId`, `String email`, `Instant occurredAt`) e `UserFacts.java` com `REGISTERED_KEY`, `EMAIL_CHANGED_KEY`, `REGISTERED_V1`, `EMAIL_CHANGED_V1` e `TYPES`, no molde de `OrderFacts`
- [X] T012 No `identity`: `platform-outbox`, `platform-messaging` e AMQP em `services/identity/pom.xml`; `services/identity/src/main/resources/db/migration/V2__create_outbox_table.sql` com a mesma DDL de `outbox_messages` de `services/orders/src/main/resources/db/migration/V1__create_orders_tables.sql`; RabbitMQ e `orderflow.outbox.poll-interval` em `services/identity/src/main/resources/application.yml`, como no `orders`; bean `FactTypes` com `UserFacts.TYPES` em `services/identity/src/main/java/com/matheus/orderFlow/user/UserMessagingConfig.java`; em `UserService.createUser` e `UserService.updateEmail`, `saveAndFlush` e depois `OutboxWriter.record` com o fato, usando `getUpdatedAt()` do usuário gravado como `occurredAt`; `UserServiceTest` ajustado
- [X] T013 Criar `services/notifications/src/main/resources/db/migration/V1__create_notifications_tables.sql`: `contacts` (`user_id` chave, `email` `VARCHAR(150) NOT NULL`, `updated_at` `TIMESTAMP(6) NOT NULL`) e `notifications` (campos do [data-model.md](data-model.md), `last_error` `VARCHAR(300)`, único em `(order_id, order_status)`, índice em `(state, next_attempt_at)`), com UUID em `BINARY(16)` como nos outros serviços
- [X] T014 Criar `services/notifications/src/main/java/com/matheus/orderFlow/FactTypesConfig.java` com um `FactTypes` que junta `OrderFacts.TYPES` e `UserFacts.TYPES`
- [X] T015 Criar o pacote `contact`: `Contact.java` (entidade package-private, `email` obrigatório "até 150 caracteres", `replaceIfNewer(String email, Instant updatedAt)` devolvendo se mudou), `ContactRepository.java`, `ContactService.java` (pública: `emailOf(UUID userId)` e `record(UUID userId, String email, Instant updatedAt)`), `UserContactListener.java` e `ContactMessagingConfig.java` (fila `user.contacts.notifications` ligada a `user.registered` e `user.email-changed`, no container resiliente)

**Checkpoint**: cadastro e troca de e-mail no `identity` aparecem na tabela `contacts` do `notifications`

---

## Phase 3: User Story 1 - E-mail de confirmação (Priority: P1) 🎯 MVP

**Goal**: o cliente recebe um e-mail com o número e o total quando o pedido é confirmado, uma vez só (FR-001, FR-002, FR-004 a FR-006, FR-008)

**Independent Test**: confirmar um pedido de um cliente com contato e ver a mensagem no Mailpit; entregar o mesmo fato duas vezes e ver uma mensagem só

### Tests for User Story 1

- [X] T016 [P] [US1] Criar `services/notifications/src/test/java/com/matheus/orderFlow/notification/NotificationTest.java`: nasce `PENDING` com `attempts` 0; `sent()` vira `SENT`; `failed(erro, agora)` soma uma tentativa e agenda a próxima para 30 s × 2^(n−1) (30 s, 1 min, 2 min, 4 min); a 5ª falha vira `FAILED`; `withoutRecipient(agora)` segue o mesmo atraso e a 5ª vira `NO_RECIPIENT`; `lastError` guarda "até 300 caracteres"; um estado final recusa qualquer mudança e o objeto não muda
- [X] T017 [P] [US1] Criar `services/notifications/src/test/java/com/matheus/orderFlow/notification/NotificationMessagesTest.java`: assunto e corpo de cada status como em [emails.md](contracts/emails.md); total `R$ 1.234,56`
- [X] T018 [P] [US1] Criar `services/notifications/src/test/java/com/matheus/orderFlow/notification/NotificationSenderTest.java`, com tudo mockado: contato ausente conta tentativa sem chamar o `MailSender`; falha do `MailSender` conta tentativa e guarda o erro; sucesso marca `SENT`; o remetente vem de `MAIL_FROM`
- [X] T019 [P] [US1] Criar `services/notifications/src/test/java/com/matheus/orderFlow/notification/NotificationControllerTest.java` (`@WebMvcTest`): `GET /admin/notifications` responde 400 para `state` desconhecido e usa `FAILED`, 20 por página e a mudança mais recente primeiro como padrão; o 403 para `USER` e a ausência do e-mail no JSON ficam no IT, porque a fatia web não carrega a segurança, como nos outros serviços
- [X] T020 [P] [US1] Criar `services/notifications/src/test/java/com/matheus/orderFlow/integration/OrderConfirmedNotificationIT.java`: `order.confirmed.v1` publicado no RabbitMQ chega ao Mailpit para o e-mail do contato, com número e total; o mesmo fato duas vezes grava uma notificação e manda um e-mail (SC-002); sem contato, a notificação fica `PENDING` com `attempts` 1; com o contato gravado e `next_attempt_at` antecipado pelo `JdbcTemplate`, o e-mail sai; `GET /admin/notifications` responde 403 para `USER` e, para `ADMIN`, lista sem e-mail nem `userId`

### Implementation for User Story 1

- [X] T021 [P] [US1] Criar `notification/OrderStatus.java` (`CONFIRMED`, `SHIPPED`, `DELIVERED`, `CANCELLED`), `notification/NotificationState.java` (`PENDING`, `SENT`, `NO_RECIPIENT`, `FAILED`, `SUPERSEDED`) e `notification/Notification.java` (entidade package-private com os campos e as transições do [data-model.md](data-model.md); no máximo 5 tentativas; `lastError` "até 300 caracteres")
- [X] T022 [US1] Criar `notification/NotificationRepository.java` (existência por pedido e status; as pendentes com `next_attempt_at` vencido, na ordem de `order_version`, em lotes de 50; página por estado, da mais recente para a mais antiga) e `notification/NotificationService.java` (pública: `register` grava `PENDING` só se não existir notificação do mesmo pedido e status; `due()`; e a gravação do resultado de cada envio em transação própria, chamada de outro bean por causa do proxy)
- [X] T023 [US1] Criar `notification/NotificationMessages.java` com os textos de [emails.md](contracts/emails.md) e `notification/NotificationSender.java` (`@Scheduled` com `orderflow.notifications.sender.poll-interval`; para cada pendente vencida, busca o contato em `ContactService`, manda pelo `MailSender` e grava `sent`, `failed` ou `withoutRecipient`; o esgotamento gera log de erro)
- [X] T024 [US1] Criar `notification/OrderStatusListener.java` e `notification/NotificationMessagingConfig.java`: fila `order.status.notifications` ligada a `order.confirmed`, no container resiliente; o listener só chama `NotificationService.register`
- [X] T025 [US1] Criar `notification/NotificationController.java` (`GET /admin/notifications`, `@PreAuthorize("hasRole('ADMIN')")`, `state` padrão `FAILED`, `size` padrão 20, `PageResponse` por `Page.map`), `notification/NotificationResponse.java` e `notification/OpenApiConfig.java`, conforme [http.md](contracts/http.md)
- [ ] T026 [US1] Em `e2e/src/test/java/com/matheus/orderFlow/e2e/AbstractE2E.java`, expor `notifications` saudável e o `mailpit` na 8025, com um ajudante que lê as mensagens de um destinatário pela API do Mailpit; criar `e2e/src/test/java/com/matheus/orderFlow/e2e/NotificationsE2E.java`: cadastro, compra e confirmação pela porta 8080 geram a mensagem com número e total em até 1 minuto (SC-001); com o `mailpit` parado, confirmar, enviar e entregar respondem como sempre, e a mensagem chega depois que ele volta (SC-003)

**Checkpoint**: US1 funciona sozinha; `./mvnw verify` verde

---

## Phase 4: Carga inicial dos contatos (FR-009, SC-005)

**Goal**: os clientes cadastrados antes do serviço têm e-mail conhecido, sem sobrescrever um mais novo vindo de fato

**Independent Test**: com usuários no `identity` e nenhum contato, a carga grava um por usuário; a segunda responde 0

### Tests

- [X] T027 [P] Criar `services/identity/src/test/java/com/matheus/orderFlow/integration/UserExportIT.java`: `GET /internal/users/export` responde 403 para `USER`; pagina na ordem do cadastro; cada item tem só `userId`, `email` e `updatedAt`, sem hash, papel ou nome no JSON
- [X] T028 [P] Criar `services/notifications/src/test/java/com/matheus/orderFlow/integration/ContactImportIT.java`, com o `identity` no WireMock: a carga percorre todas as páginas repassando a credencial de quem chamou e responde `imported`; a segunda responde 0; um contato mais novo, vindo de fato, não é sobrescrito; com o `identity` fora, responde 503 e o que já foi gravado fica; `USER` recebe 403 e nada chega ao `identity`
- [X] T029 [P] Criar `services/notifications/src/test/java/com/matheus/orderFlow/contact/ContactImportControllerTest.java` (`@WebMvcTest`): 200 com `{"imported": n}`; o 403 para `USER` fica no `ContactImportIT`, que carrega a segurança

### Implementation

- [X] T030 No `identity`: `user/UserExportController.java` (`GET /internal/users/export`, `ADMIN`, `page` 0 e `size` 100 como padrão), `user/UserExportResponse.java` e a consulta paginada em `UserService`; `/internal/**` em `springdoc.paths-to-exclude` de `services/identity/src/main/resources/application.yml`
- [X] T031 No `notifications`: `spring-boot-starter-restclient` no `pom.xml`, `spring.http.serviceclient.identity.base-url: ${IDENTITY_URL:http://identity:8080}` no `application.yml`, `contact/UserExportClient.java` (`@HttpExchange("/internal/users")`), `contact/IdentityClientConfig.java` (`@ImportHttpServices` do grupo `identity` com `BearerTokenPropagationInterceptor`, como `OrdersClientConfig` do `sales`), `contact/ContactImportService.java` (páginas de 100, `ContactService.record`, 503 por `ServiceUnavailableException`), `contact/ContactImportController.java` (`POST /admin/notifications/contacts/import`, `ADMIN`) e `contact/ImportResponse.java`

**Checkpoint**: um cliente antigo, depois da carga, recebe o e-mail de confirmação

---

## Phase 5: User Story 2 - E-mails de envio e entrega (Priority: P2)

**Goal**: o cliente recebe um e-mail quando o pedido é enviado e outro quando é entregue, nunca o de um status antigo depois do novo (FR-001, FR-003)

**Independent Test**: avançar um pedido confirmado para enviado e para entregue e ver duas mensagens, na ordem

### Tests for User Story 2

- [X] T032 [P] [US2] Em `contracts/src/test/java/com/matheus/orderFlow/contracts/order/FactFormatTest.java`, `userId` em `order.shipped.v1` e `order.delivered.v1`; em `FactCompatibilityTest.java`, uma mensagem desses fatos sem `userId` chega com `userId` nulo
- [X] T033 [P] [US2] Em `NotificationSenderTest.java`: uma pendente vira `SUPERSEDED`, sem chamar o `MailSender`, quando o mesmo pedido já tem `SENT` com versão maior; `userId` nulo conta como contato ausente
- [X] T034 [P] [US2] Criar `services/notifications/src/test/java/com/matheus/orderFlow/integration/OrderProgressNotificationIT.java`: `order.shipped` e `order.delivered` geram duas mensagens, na ordem da versão; com `order.delivered` já enviado, um `order.shipped` atrasado fica `SUPERSEDED` e não sai (SC-004)

### Implementation for User Story 2

- [X] T035 [US2] Acrescentar `UUID userId`, depois de `orderId`, em `contracts/.../order/OrderShippedEvent.java` e `OrderDeliveredEvent.java`; em `services/orders/src/main/java/com/matheus/orderFlow/order/OrderService.java`, passar o dono do pedido em `ship` e `deliver`; ajustar os construtores em `services/sales/src/test/java/com/matheus/orderFlow/sales/OrderViewProjectorTest.java`, `services/sales/src/test/java/com/matheus/orderFlow/integration/OrderViewProjectionIT.java` e nos testes do `orders`
- [X] T036 [US2] No `notifications`: ligar `order.shipped` e `order.delivered` na fila `order.status.notifications`, tratar os dois fatos em `OrderStatusListener` e aplicar a regra do `SUPERSEDED` em `NotificationSender`, com a consulta em `NotificationRepository`
- [X] T037 [US2] Em `specs/003-microsservicos/contracts/events.md`, `userId` nos fatos de envio e entrega (modelo flow-back)

**Checkpoint**: US1 e US2 funcionam; `./mvnw verify` verde

---

## Phase 6: User Story 3 - E-mail de cancelamento (Priority: P3)

**Goal**: o cliente recebe um e-mail quando o pedido é cancelado (FR-001)

**Independent Test**: cancelar um pedido confirmado e ver a mensagem de cancelamento

### Tests for User Story 3

- [X] T038 [P] [US3] Em `FactFormatTest.java` e `FactCompatibilityTest.java`, `userId` em `order.cancelled.v1`, como na T032
- [X] T039 [P] [US3] Criar `services/notifications/src/test/java/com/matheus/orderFlow/integration/OrderCancelledNotificationIT.java`: `order.cancelled` gera a mensagem de cancelamento

### Implementation for User Story 3

- [X] T040 [US3] Acrescentar `UUID userId` em `contracts/.../order/OrderCancelledEvent.java`; passar o dono em `OrderService.cancel`; ajustar os construtores nos testes do `sales` e do `orders`; ligar `order.cancelled` na fila `order.status.notifications` e tratá-lo em `OrderStatusListener`; `userId` no cancelamento em `specs/003-microsservicos/contracts/events.md`

**Checkpoint**: as três user stories funcionam; `./mvnw verify` verde

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T041 [P] Criar `e2e/src/test/java/com/matheus/orderFlow/e2e/FailureIsolationE2E.java`: com `payment`, `sales` e `notifications` parados ao mesmo tempo, criar, confirmar, enviar, entregar e cancelar pedidos funciona; ao subirem, cada um recebe os fatos, e cada e-mail chega uma vez (SC-002 da spec 003); marcar a T108 em `specs/003-microsservicos/tasks.md`
- [ ] T042 [P] Notificações nos testes ponta a ponta existentes: em `FullPurchaseE2E.java`, as mensagens de confirmação, envio e entrega; em `OrderFactsDeliveryE2E.java`, o `notifications` parado durante as mudanças recebe os fatos ao subir, com um e-mail por status; em `DataOwnershipE2E.java`, o usuário `notifications` do MySQL sem acesso aos outros schemas, o do RabbitMQ sem consumir as filas dos outros nem publicar na exchange, o `identity` sem consumir a fila de contatos nem publicar fato de pedido, e o `orders` sem publicar fato de usuário, com a permissão por chave de roteamento em `docker/rabbitmq/users.sh` (R12); em `DocumentationE2E.java`, a entrada `notifications`
- [X] T043 Atualizar `README.md`: tabela de serviços, dados e portas (Mailpit na 8025), desenho da arquitetura, senhas novas na tabela de variáveis (`RABBITMQ_IDENTITY_PASSWORD`, `RABBITMQ_NOTIFICATIONS_PASSWORD`), passo de volume antigo (schema `notifications` e carga inicial), resumo das decisões com link para [research.md](research.md), contagem de testes por módulo e roteiro
- [X] T044 Conferir as descrições do `OpenApiConfig` e as anotações Swagger do `notifications` e do `identity` contra [http.md](contracts/http.md)
- [ ] T045 Executar os cenários 1 a 10 de [quickstart.md](quickstart.md) e confirmar `./mvnw verify` e `./mvnw verify -Pe2e` verdes no CI

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sem dependências
- **Foundational (Phase 2)**: depende do Setup; bloqueia todas as user stories
- **US1 (Phase 3)**: depende da Foundational
- **Carga (Phase 4)**: depende da Foundational; independente da US1, mas só tem efeito visível com ela
- **US2 (Phase 5)** e **US3 (Phase 6)**: dependem da US1 (registro, envio e fila); são independentes entre si, salvo os arquivos de teste de contrato, que mudam em sequência (T032 antes da T038)
- **Polish (Phase 7)**: depende das três user stories e da carga

### Within Each Phase

- Testes primeiro, falhando
- Entidade antes da service, service antes do listener e da controller
- `./mvnw verify` verde no fim de cada fase, com commit próprio

### Parallel Opportunities

- Setup: T003, T004 e T006 em paralelo
- Foundational: T007 a T010 em paralelo; T011 em paralelo com T013
- US1: T016 a T020 em paralelo; T021 em paralelo com T023 depois da T022
- Carga: T027 a T029 em paralelo; T030 e T031 em paralelo
- Polish: T041 e T042 em paralelo

---

## Parallel Example: User Story 1

```bash
Task: "NotificationTest em services/notifications/.../notification/NotificationTest.java"
Task: "NotificationMessagesTest em services/notifications/.../notification/NotificationMessagesTest.java"
Task: "NotificationControllerTest em services/notifications/.../notification/NotificationControllerTest.java"
Task: "OrderConfirmedNotificationIT em services/notifications/.../integration/OrderConfirmedNotificationIT.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Setup e Foundational
2. US1: e-mail de confirmação
3. **Parar e validar**: cenários 3, 4 e 6 do [quickstart.md](quickstart.md)

### Incremental Delivery

1. Setup + Foundational → contatos chegando
2. US1 → confirmação (MVP)
3. Carga inicial → clientes antigos incluídos
4. US2 → envio e entrega
5. US3 → cancelamento
6. Polish → falha isolada (T108 da spec 003) e README

---

## Notes

- Commit por fase, com `./mvnw verify` verde, Conventional Commits em português e sem atribuição a IA
- Nenhum comentário no código; o porquê vai para o README e para o [research.md](research.md)
- Proxy: a transação de cada envio fica num bean diferente do que a chama (armadilha do `@Transactional` em chamada interna)
