---

description: "Tarefas da separação em microsserviços"
---

# Tasks: Separação em microsserviços

**Input**: Design documents from `/specs/003-microsservicos/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Tests**: incluídos. A constituição (princípio III) exige teste em toda mudança de
comportamento, e teste antes do código quando a regra já é conhecida. Nas fases das
user stories, as tarefas de teste vêm antes da implementação e devem falhar antes
dela.

**Organization**: uma fase por user story, na ordem de prioridade da spec. Cada
fase termina com `./mvnw verify` verde, README em dia e contagem de testes
atualizada (padrões de trabalho da constituição).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: pode rodar em paralelo (arquivos diferentes, sem dependência pendente)
- **[Story]**: user story da tarefa (US1 a US7)

## Path Conventions

- Raiz: `pom.xml` agregador, `Dockerfile`, `docker-compose.yml`, `docker/`
- Bibliotecas: `platform/platform-*/`, `contracts/`
- Aplicações: `gateway/`, `monolith/` (até a US7), `services/<serviço>/`
- Testes ponta a ponta: `e2e/`
- Código Java em `src/main/java/com/matheus/orderFlow/...`; classe de aplicação de
  cada serviço em `com.matheus.orderFlow` (research R17)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: transformar o projeto em multi-módulo sem mudar comportamento.

- [ ] T001 Depois do merge do PR de `chore/adota-spec-kit` em `main`, rebasear `spec/microsservicos` sobre `origin/main`, trazendo o bloco `permissions` de `.github/workflows/ci.yml` e o `mongo:7` de `docker-compose.yml` e `src/test/java/com/matheus/orderFlow/TestcontainersConfiguration.java`; confirmar `./mvnw verify` verde com 231 unitários e 134 de integração
- [ ] T002 Transformar `pom.xml` em agregador e parent: `<packaging>pom</packaging>`, parent `spring-boot-starter-parent` 4.1.1, `<dependencyManagement>` com o BOM `spring-cloud-dependencies` 2025.1.3, `springdoc-openapi-starter-webmvc-ui` 3.1.1, `resilience4j-spring-boot3` 2.3.0 e WireMock 3.9.1, e `<pluginManagement>` com Surefire (`*Test`), Failsafe (`*IT`) e o perfil `e2e` (Failsafe incluindo `*E2E`)
- [ ] T003 Mover a aplicação para o módulo `monolith` com `git mv src monolith/src` e criar `monolith/pom.xml` com as dependências e plugins de hoje (incluindo o `springdoc-openapi-maven-plugin`); registrar `monolith` em `<modules>` de `pom.xml`; confirmar `./mvnw verify` verde com as mesmas contagens
- [ ] T004 Criar os módulos vazios e registrá-los em `<modules>` de `pom.xml`: `platform/platform-web/pom.xml`, `platform/platform-security/pom.xml`, `platform/platform-messaging/pom.xml`, `platform/platform-outbox/pom.xml`, `platform/platform-test/pom.xml`, `contracts/pom.xml`, `gateway/pom.xml` e `e2e/pom.xml` (o `e2e` só entra no build com o perfil `e2e`)
- [ ] T005 [P] Reescrever `Dockerfile` com `ARG MODULE`: o estágio de build copia os POMs, o `.mvn`, o `mvnw` e os fontes, roda `./mvnw -B -pl ${MODULE} -am package -DskipTests`; o estágio final copia `${MODULE}/target/*.jar`, mantendo o usuário `orderflow` sem shell; ajustar `.dockerignore` para ignorar `**/target`
- [ ] T006 [P] Criar `docker/mysql/init/01-schemas.sql` com os schemas `orderflow`, `identity`, `catalog`, `orders` e `payment` e os usuários `identity`, `catalog`, `orders` e `payment`, cada um com `ALL` só no próprio schema; o usuário `orderflow` (monólito) fica com `ALL` em `orderflow`; montar a pasta em `/docker-entrypoint-initdb.d` no serviço `mysql` de `docker-compose.yml`
- [ ] T007 Ajustar `.github/workflows/ci.yml` para multi-módulo: `./mvnw -B verify` na raiz, upload de `**/target/surefire-reports/` e `**/target/failsafe-reports/` na falha, `report_paths: '**/target/*-reports/TEST-*.xml'` no relatório, e um job `e2e` (depois do `build`) com `./mvnw -B verify -Pe2e -pl e2e -am`

**Checkpoint**: build multi-módulo verde, com o `monolith` idêntico à aplicação de hoje.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: bibliotecas compartilhadas, fatos com tipo versionado e rastreamento,
ainda dentro do monólito. Nenhuma user story começa antes desta fase.

- [ ] T008 Mover `OrderConfirmedEvent`, `OrderShippedEvent`, `OrderDeliveredEvent` e `OrderCancelledEvent` de `monolith/src/main/java/com/matheus/orderFlow/order/` para `contracts/src/main/java/com/matheus/orderFlow/contracts/order/`, mantendo os campos de [contracts/events.md](contracts/events.md); mover `OrderConfirmedEventTest` para `contracts/src/test/java/com/matheus/orderFlow/contracts/order/`; atualizar os imports em `order`, `payment` e `sales` do `monolith`
- [ ] T009 [P] Mover `shared/exception/*` e `shared/web/PageResponse.java` de `monolith/src/main/java/com/matheus/orderFlow/` para `platform/platform-web/src/main/java/com/matheus/orderFlow/shared/`, sem mudar o pacote; mover `GlobalExceptionHandlerTest` para `platform/platform-web/src/test/java/com/matheus/orderFlow/shared/exception/`; `monolith` passa a depender de `platform-web`
- [ ] T010 Criar `contracts/src/main/java/com/matheus/orderFlow/contracts/order/OrderFacts.java` com as chaves de roteamento (`order.confirmed`, `order.shipped`, `order.delivered`, `order.cancelled`), os ids lógicos (`order.confirmed.v1`, `order.shipped.v1`, `order.delivered.v1`, `order.cancelled.v1`) e os ids legados (`com.matheus.orderFlow.order.OrderConfirmedEvent` e os demais), mapeados para as classes de T008
- [ ] T011 Mover `shared/messaging/RabbitMqConfig.java` para `platform/platform-messaging/src/main/java/com/matheus/orderFlow/shared/messaging/RabbitMqConfig.java`, mantendo a exchange `orderflow.events`, a DLX `orderflow.events.dlx`, a DLQ `orderflow.events.dlq` e o `resilientListenerContainerFactory`; as constantes de chave de roteamento passam a vir de `OrderFacts`; `platform-messaging` depende de `contracts`
- [ ] T012 Escrever primeiro `platform/platform-messaging/src/test/java/com/matheus/orderFlow/shared/messaging/FactTypeMappingTest.java`: (a) publicar `OrderConfirmedEvent` grava `__TypeId__` = `order.confirmed.v1`; (b) uma mensagem com `__TypeId__` = `com.matheus.orderFlow.order.OrderConfirmedEvent` é convertida; (c) um corpo com um campo a mais, desconhecido, é convertido sem erro (FR-019, SC-011)
- [ ] T013 Configurar em `RabbitMqConfig.java` o `JacksonJsonMessageConverter` com um `DefaultJacksonJavaTypeMapper` que usa `setIdClassMapping` com os ids lógicos e legados de `OrderFacts`, até `FactTypeMappingTest` passar
- [ ] T014 [P] Mover `shared/outbox/*` e `shared/config/SchedulingConfig.java` para `platform/platform-outbox/src/main/java/com/matheus/orderFlow/shared/`, com `OutboxMessageTest` e `OutboxRelayTest` em `platform/platform-outbox/src/test/java/com/matheus/orderFlow/shared/outbox/`; a DDL do outbox continua no Flyway de cada serviço
- [ ] T015 Acrescentar `spring-boot-starter-zipkin` a `monolith/pom.xml` e, em `monolith/src/main/resources/application.yml`, amostragem 1.0, endpoint do Zipkin por `ZIPKIN_ENDPOINT`, observação ligada no `RabbitTemplate` e nos listeners, e o id do rastro no padrão de log (nomes das propriedades conferidos no classpath do Boot 4.1.1); acrescentar o serviço `zipkin` (`openzipkin/zipkin`, porta 9411) a `docker-compose.yml`
- [ ] T016 Escrever primeiro, em `platform/platform-outbox/src/test/java/com/matheus/orderFlow/shared/outbox/OutboxRelayTest.java`: o `OutboxWriter` grava o `traceparent` do rastro corrente; o `OutboxRelay` publica com cabeçalho `traceparent` do mesmo rastro; uma mensagem com `traceparent` nulo é publicada normalmente
- [ ] T017 Implementar o rastro no outbox: campo `traceparent` ("`VARCHAR(55)`, nulo") em `platform/platform-outbox/src/main/java/com/matheus/orderFlow/shared/outbox/OutboxMessage.java`; captura pelo `Propagator` do Micrometer Tracing em `OutboxWriter.java`; restauração como contexto pai antes do envio em `OutboxRelay.java`; migração `monolith/src/main/resources/db/migration/V8__add_traceparent_to_outbox.sql` com `ALTER TABLE outbox_messages ADD COLUMN traceparent VARCHAR(55) NULL`
- [ ] T018 Escrever `monolith/src/test/java/com/matheus/orderFlow/integration/TracePropagationIT.java`: a mensagem `order.confirmed` publicada pelo relay chega ao consumidor com o mesmo id de rastro da requisição HTTP que confirmou o pedido (FR-016)

**Checkpoint**: monólito verde, fatos com tipo lógico, rastro atravessando o outbox.

---

## Phase 3: User Story 1 - Entrada única e credenciais verificáveis por qualquer serviço (Priority: P1) 🎯 MVP

**Goal**: o cliente usa só a porta 8080 do gateway, com a API de hoje, e a
credencial passa a ser RS256, verificável sem o segredo de emissão.

**Independent Test**: rodar `RoutesThroughGatewayE2E` e verificar que um token
assinado fora do emissor é recusado ([quickstart](quickstart.md), cenários 3, 7 e 10).

### Implementation (estrutura, sem mudança de comportamento)

- [ ] T019 [US1] Separar a segurança: `SecurityErrorHandlers.java`, `AuthenticatedUser.java` e a cadeia de filtros de `SecurityConfig.java` vão para `platform/platform-security/src/main/java/com/matheus/orderFlow/shared/security/`, com as rotas públicas lidas de `orderflow.security.public-paths` e `orderflow.security.public-get-paths` (documentação e `/actuator/health` sempre públicas); `TokenService.java`, `RefreshToken.java`, `RefreshTokenRepository.java`, `RefreshTokenService.java`, `RotatedToken.java`, o `PasswordEncoder` e o `JwtEncoder` vão para `monolith/src/main/java/com/matheus/orderFlow/user/` (`IssuerConfig.java` para os beans); `TokenServiceTest` vai para `monolith/src/test/java/com/matheus/orderFlow/user/`; em `monolith/src/main/resources/application.yml`, `public-paths: /auth/**, /.well-known/jwks.json` e `public-get-paths: /products, /products/*`

### Tests for User Story 1 ⚠️

- [ ] T020 [US1] Atualizar `monolith/src/test/java/com/matheus/orderFlow/user/TokenServiceTest.java`: cabeçalho `alg` RS256 com `kid`; claims `iss` = `orderflow`, `sub`, `roles`, `iat` e `exp` em 15 minutos
- [ ] T021 [P] [US1] Criar `monolith/src/test/java/com/matheus/orderFlow/integration/JwksIT.java`: `GET /.well-known/jwks.json` sem credencial responde 200 com `kty` `RSA`, `kid`, `alg` `RS256`, `use` `sig`, `n` e `e`, e nunca `d`, `p` ou `q`
- [ ] T022 [P] [US1] Acrescentar a `monolith/src/test/java/com/matheus/orderFlow/integration/SecurityIT.java`: token assinado com outra chave RSA recebe 401; token HS256 assinado com o segredo antigo recebe 401 (FR-004, cenário 3 da US1)
- [ ] T023 [P] [US1] Acrescentar a `monolith/src/test/java/com/matheus/orderFlow/integration/RefreshTokenIT.java`: `POST /auth/refresh` só com o refresh token devolve um par novo com token de acesso RS256, sem senha (FR-011, SC-007)

### Implementation for User Story 1

- [ ] T024 [US1] Criar `monolith/src/main/java/com/matheus/orderFlow/user/JwtKeyConfig.java`: chave RSA de `orderflow.security.jwt.private-key` (PEM PKCS#8) ou par de 2048 bits gerado ao subir quando ausente; `kid` pelo thumbprint da chave pública; `JwtEncoder` RS256 e `JwtDecoder` com a chave pública; `TokenService.java` assina com cabeçalho RS256; remover `orderflow.security.jwt.secret` e todo código HS256 de `application.yml` e `SecurityConfig.java`
- [ ] T025 [US1] Criar `monolith/src/main/java/com/matheus/orderFlow/user/JwksController.java` com `GET /.well-known/jwks.json` devolvendo só o conjunto de chaves públicas ([contracts/http-internal.md](contracts/http-internal.md))
- [ ] T026 [P] [US1] Criar `platform/platform-test/src/main/java/com/matheus/orderFlow/shared/test/TestTokens.java` (par RSA por JVM e `bearer(userId, role)` com `iss`, `sub`, `roles` e `exp`) e `TestJwtDecoderConfiguration.java` (`@TestConfiguration` com `JwtDecoder` da chave pública de teste), para os serviços que não emitem token

### Gateway

- [ ] T027 [US1] Escrever primeiro `gateway/src/test/java/com/matheus/orderFlow/gateway/GatewayRoutingIT.java` com WireMock no lugar do `monolith`: rotas chegam com método, caminho, corpo e `Authorization` intactos; `/internal/**`, `/.well-known/jwks.json` e `/actuator/**` (exceto a saúde do próprio gateway) respondem 404 em `ErrorResponse`; destino fora do ar responde 503 com `message` "serviço indisponível"; destino lento responde 504 com "serviço não respondeu a tempo"; 4xx do destino passam sem alteração ([contracts/gateway-routes.md](contracts/gateway-routes.md))
- [ ] T028 [US1] Criar `gateway/src/main/java/com/matheus/orderFlow/GatewayApplication.java` e `gateway/src/main/resources/application.yml` com Spring Cloud Gateway Server WebMVC 5.0.3 (nomes das propriedades conferidos no classpath): rota padrão para `${MONOLITH_URL:http://monolith:8080}`, bloqueio das rotas internas, timeout, `spring-boot-starter-zipkin`; tradução de falha do destino para `ErrorResponse` 503/504 em `gateway/src/main/java/com/matheus/orderFlow/gateway/UpstreamErrorHandling.java`, até `GatewayRoutingIT` passar
- [ ] T029 [US1] Escrever primeiro `gateway/src/test/java/com/matheus/orderFlow/gateway/DocumentationIT.java`: `/swagger-ui.html` responde e a configuração lista uma entrada por serviço; depois acrescentar `springdoc-openapi-starter-webmvc-ui` ao gateway com `springdoc.swagger-ui.urls` (por enquanto só `monolith`) e a rota `/v3/api-docs/{serviço}` para o `/v3/api-docs` de cada serviço em `gateway/src/main/resources/application.yml`

### Execução e ponta a ponta

- [ ] T030 [US1] Em `docker-compose.yml`: o serviço `app` vira `monolith` (`MODULE=monolith`, sem porta publicada); serviço `gateway` (`MODULE=gateway`, porta 8080 publicada); `ZIPKIN_ENDPOINT` nos dois; healthchecks; `JAVA_TOOL_OPTIONS=-Xmx256m` em cada aplicação
- [ ] T031 [US1] Criar `e2e/src/test/java/com/matheus/orderFlow/e2e/AbstractE2E.java`: sobe `../docker-compose.yml` pelo Testcontainers (classe de Compose conferida no classpath do 2.0.5), expõe `gateway:8080` e a porta interna de cada serviço, e oferece cadastro, login e token de administrador (`ADMIN_EMAIL`, `ADMIN_PASSWORD`)
- [ ] T032 [P] [US1] Criar `e2e/src/test/java/com/matheus/orderFlow/e2e/RoutesThroughGatewayE2E.java`: cada rota de hoje (`/auth`, `/users`, `/products`, `/cart`, `/orders`, `/payments`, `/admin/sales`) responde pela porta 8080 com o mesmo status e o mesmo formato de corpo e de `ErrorResponse` (SC-001)
- [ ] T033 [P] [US1] Criar `e2e/src/test/java/com/matheus/orderFlow/e2e/DirectCallWithoutCredentialE2E.java`: chamada direta a `monolith:8080/orders` sem token recebe 401; com token assinado por chave estranha, 401 (SC-009)
- [ ] T034 [P] [US1] Criar `e2e/src/test/java/com/matheus/orderFlow/e2e/DocumentationE2E.java`: a união dos documentos listados em `/swagger-ui.html` cobre todas as rotas públicas (SC-012)
- [ ] T035 [US1] Atualizar `README.md`: entrada única, credencial RS256 com JWKS e o motivo, Zipkin, `docker compose up --build`, `./mvnw verify -Pe2e`, remoção do `JWT_SECRET`, comando para aplicar `docker/mysql/init/01-schemas.sql` em volumes existentes, contagem de testes por módulo

**Checkpoint**: a API inteira funciona pela porta 8080, com credencial RS256. Depois
desta fase, a spec 002 (notificações) pode ser implementada sobre a mesma base.

---

## Phase 4: User Story 2 - Relatórios de vendas como serviço próprio (Priority: P2)

**Goal**: os relatórios saem do monólito e acompanham os pedidos só pelos fatos.

**Independent Test**: `SalesIsolationE2E` ([quickstart](quickstart.md), cenários 4 e 9).

### Tests for User Story 2 ⚠️

- [ ] T036 [P] [US2] Criar `monolith/src/test/java/com/matheus/orderFlow/integration/OrderExportIT.java`: `ADMIN` recebe só pedidos já confirmados, com `orderId`, `userId`, `status`, `total`, `items`, `version` e `confirmedAt`; `size` aceita "1 a 500, padrão 100" e responde 400 fora disso; `USER` recebe 403; sem token, 401
- [ ] T037 [P] [US2] Acrescentar a `platform/platform-web/src/test/java/com/matheus/orderFlow/shared/exception/GlobalExceptionHandlerTest.java`: `ServiceUnavailableException` vira 503 em `ErrorResponse`
- [ ] T038 [P] [US2] Criar `platform/platform-security/src/test/java/com/matheus/orderFlow/shared/security/BearerTokenPropagationInterceptorTest.java`: a chamada de saída leva o `Authorization` da requisição corrente; sem requisição corrente, não leva nada

### Implementation for User Story 2

- [ ] T039 [US2] Criar `platform/platform-web/src/main/java/com/matheus/orderFlow/shared/exception/ServiceUnavailableException.java` e o tratamento 503 em `GlobalExceptionHandler.java`; criar `platform/platform-security/src/main/java/com/matheus/orderFlow/shared/security/BearerTokenPropagationInterceptor.java`; configurar em `platform-security` o `JwtDecoder` por `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`, com cache das chaves de pelo menos 15 minutos (validade do token), quando o serviço não declara o próprio decoder
- [ ] T040 [US2] Criar `monolith/src/main/java/com/matheus/orderFlow/order/OrderExportController.java` (`GET /internal/orders/export`, `@PreAuthorize("hasRole('ADMIN')")`), `OrderExportResponse.java` e a consulta paginada em `OrderService.java`, com `confirmedAt` vindo da mensagem `order.confirmed` do próprio outbox, reaproveitando o SQL de `OrderViewRebuilder.java`
- [ ] T041 [US2] Criar `services/sales/pom.xml` (`platform-web`, `platform-security`, `platform-messaging`, Spring Data MongoDB, springdoc, `spring-boot-starter-zipkin`; testes com `platform-test`, Testcontainers MongoDB e RabbitMQ, WireMock) e `services/sales/src/main/java/com/matheus/orderFlow/SalesApplication.java`; registrar o módulo em `pom.xml`
- [ ] T042 [US2] Mover com `git mv` o pacote `monolith/src/main/java/com/matheus/orderFlow/sales/` para `services/sales/src/main/java/com/matheus/orderFlow/sales/`, apagando `OrderViewRebuilder.java` e `OrderViewRebuildCommand.java`; criar `services/sales/src/main/resources/application.yml` (MongoDB, RabbitMQ, `jwk-set-uri`, `ORDERS_URL`, rastreamento)
- [ ] T043 [US2] Mover `OrderViewProjectorTest` e `OrderViewTest` para `services/sales/src/test/java/com/matheus/orderFlow/sales/`, e `OrderViewProjectionIT`, `SalesReportIT`, `MongoIT` e `DeadLetterIT` para `services/sales/src/test/java/com/matheus/orderFlow/integration/`, com `AbstractIntegrationTest.java` e `TestcontainersConfiguration.java` próprios (MongoDB e RabbitMQ), usando `TestTokens`
- [ ] T044 [US2] Escrever primeiro `services/sales/src/test/java/com/matheus/orderFlow/integration/SalesRebuildIT.java` com WireMock no lugar de `/internal/orders/export`: `POST /admin/sales/rebuild` como `ADMIN` responde 200 com `{"rebuilt": n}` e relatórios corretos; um documento com versão mais nova não é sobrescrito por dado mais velho; o `Authorization` chega à exportação; exportação fora do ar responde 503; `USER` recebe 403
- [ ] T045 [US2] Implementar a reconstrução: `services/sales/src/main/java/com/matheus/orderFlow/sales/OrderExportClient.java` (`@HttpExchange`, "10 s por página", `BearerTokenPropagationInterceptor`), `SalesRebuildService.java` (laço paginado com a mesma gravação guardada por versão do projetor), `POST /admin/sales/rebuild` em `SalesController.java` e `RebuildResponse.java`, até `SalesRebuildIT` passar
- [ ] T046 [US2] Criar a descrição da API em `services/sales/src/main/java/com/matheus/orderFlow/sales/OpenApiConfig.java` e tirar a seção de vendas de `monolith/src/main/java/com/matheus/orderFlow/shared/config/OpenApiConfig.java`
- [ ] T047 [US2] Tirar vendas do monólito: dependência do MongoDB em `monolith/pom.xml`, container MongoDB em `monolith/src/test/java/com/matheus/orderFlow/TestcontainersConfiguration.java`, `MongoTemplate` e limpeza de `order_views` em `monolith/src/test/java/com/matheus/orderFlow/integration/AbstractIntegrationTest.java`, `spring.data.mongodb` em `monolith/src/main/resources/application.yml`, e `OrderViewRebuilderIT.java`
- [ ] T048 [US2] Rota `/admin/sales/**` para `${SALES_URL:http://sales:8080}` e entrada `sales` na documentação em `gateway/src/main/resources/application.yml`, com o caso em `GatewayRoutingIT.java`; serviço `sales` (`MODULE=services/sales`) em `docker-compose.yml`, com o MongoDB passando do `monolith` para ele
- [ ] T049 [P] [US2] Criar `e2e/src/test/java/com/matheus/orderFlow/e2e/SalesIsolationE2E.java`: com `sales` parado, pedidos são confirmados; ao subir, `GET /admin/sales/daily` os inclui (cenário 2, SC-005); `POST /admin/sales/rebuild` com a projeção vazia deixa os relatórios iguais (cenários 1 e 3)
- [ ] T050 [US2] Atualizar `README.md`: relatórios como serviço, a reconstrução por `POST /admin/sales/rebuild` no lugar de `--orderflow.views.rebuild=true` e o motivo, contagem de testes

**Checkpoint**: relatórios fora do monólito, alimentados só por fatos e pela exportação.

---

## Phase 5: User Story 3 - Pagamentos como serviço próprio (Priority: P3)

**Goal**: a cobrança roda como serviço, disparada só pelo fato `order.confirmed`.

**Independent Test**: `PaymentIsolationE2E` ([quickstart](quickstart.md), cenário 4).

### Implementation for User Story 3

- [ ] T051 [US3] Criar `services/payment/pom.xml` (`platform-web`, `platform-security`, `platform-messaging`, Spring Data JPA, Flyway, MySQL, `resilience4j-spring-boot3`, springdoc, `spring-boot-starter-zipkin`; testes com `platform-test`, WireMock e Testcontainers MySQL e RabbitMQ) e `services/payment/src/main/java/com/matheus/orderFlow/PaymentApplication.java`; registrar o módulo
- [ ] T052 [P] [US3] Escrever primeiro `services/payment/src/test/java/com/matheus/orderFlow/integration/PaymentsTableMoveIT.java`: com a tabela `orderflow.payments` preenchida, o script `docker/mysql/migrations/us3-payments.sql` move todas as linhas para `payment.payments`, e a `V1` do serviço não faz nada depois (FR-012)
- [ ] T053 [US3] Mover com `git mv` o pacote `payment` para `services/payment/src/main/java/com/matheus/orderFlow/payment/`; copiar `JpaAuditingConfig.java`; criar `services/payment/src/main/resources/application.yml` (schema `payment`, usuário `payment`, RabbitMQ, URL do gateway de pagamento e as instâncias `paymentGateway` do Resilience4j vindas do monólito, `jwk-set-uri`, rastreamento)
- [ ] T054 [US3] Criar `services/payment/src/main/resources/db/migration/V1__create_payments_table.sql` com `CREATE TABLE IF NOT EXISTS payments` na forma final de hoje (`V3` com `V5`: `user_id`, `uk_payments_idempotency_key`, `idx_payments_order_id`, `idx_payments_user_id`)
- [ ] T055 [US3] Criar `docker/mysql/migrations/us3-payments.sql` com `RENAME TABLE orderflow.payments TO payment.payments`, até `PaymentsTableMoveIT` passar
- [ ] T056 [US3] Mover `PaymentIT`, `PaymentControllerTest` e `PaymentTest` para `services/payment/src/test/java/com/matheus/orderFlow/`, com `AbstractIntegrationTest.java` e `TestcontainersConfiguration.java` próprios (MySQL, RabbitMQ, WireMock), usando `TestTokens`
- [ ] T057 [US3] Tirar pagamentos do monólito: pacote, configuração do gateway de pagamento e do Resilience4j em `monolith/src/main/resources/application.yml`, limpeza de `payments` em `AbstractIntegrationTest.java`, seção de pagamentos de `OpenApiConfig.java`; descrição da API em `services/payment/src/main/java/com/matheus/orderFlow/payment/OpenApiConfig.java`
- [ ] T058 [US3] Rota `/payments/**` para `${PAYMENT_URL:http://payment:8080}` e entrada na documentação em `gateway/src/main/resources/application.yml`, com o caso em `GatewayRoutingIT.java`; serviço `payment` em `docker-compose.yml`, com o WireMock passando do `monolith` para ele
- [ ] T059 [P] [US3] Criar `e2e/src/test/java/com/matheus/orderFlow/e2e/PaymentIsolationE2E.java`: com `payment` parado, a confirmação é aceita; ao subir, há exatamente uma cobrança (cenários 1 e 2, SC-005); o pagamento de outro cliente responde 404 (cenário 3)
- [ ] T060 [US3] Atualizar `README.md`: pagamentos como serviço, o script de movimentação e quando rodá-lo, contagem de testes

**Checkpoint**: pagamentos fora do monólito, com os dados preservados.

---

## Phase 6: User Story 4 - Catálogo como serviço próprio (Priority: P4)

**Goal**: o catálogo sai do monólito, e carrinho e pedidos passam a consultá-lo pela rede.

**Independent Test**: `CatalogOutageE2E` ([quickstart](quickstart.md), cenário 5).

### Tests for User Story 4 ⚠️

- [ ] T061 [US4] Criar `monolith/src/test/java/com/matheus/orderFlow/integration/CatalogClientIT.java` com WireMock no lugar do `catalog`: o pedido copia nome e preço do catálogo; o `Authorization` chega ao catálogo; catálogo mais lento que "1,5 s" faz a finalização responder 503 em até 5 s com o carrinho intacto; com o circuito aberto, 503 sem chamar o catálogo; listar os próprios pedidos funciona com o catálogo fora do ar (cenário 3); o pedido mantém o preço da compra depois de o preço mudar (cenário 4)
### Implementation for User Story 4

- [ ] T062 [US4] Criar no monólito `monolith/src/main/java/com/matheus/orderFlow/catalog/CatalogClient.java` (`@HttpExchange`, `GET /products/{id}`), `CatalogProduct.java` (`id`, `name`, `price`, os únicos campos que carrinho e pedido usam) e `CatalogClientConfig.java` (`orderflow.services.catalog.url`, timeout de 1,5 s, `BearerTokenPropagationInterceptor`, circuit breaker `catalog` com fallback que lança `ServiceUnavailableException`, 404 do catálogo como `NotFoundException`)
- [ ] T063 [US4] Trocar `ProductService` por `CatalogClient` em `monolith/src/main/java/com/matheus/orderFlow/cart/CartService.java`, `cart/CartItemResponse.java` e `order/OrderService.java`, até `CatalogClientIT` passar
- [ ] T064 [US4] Criar `services/catalog/pom.xml` (`platform-web`, `platform-security`, Spring Data JPA, Flyway, MySQL, springdoc, `spring-boot-starter-zipkin`; testes com `platform-test` e Testcontainers MySQL) e `services/catalog/src/main/java/com/matheus/orderFlow/CatalogApplication.java`; registrar o módulo
- [ ] T065 [P] [US4] Escrever primeiro `services/catalog/src/test/java/com/matheus/orderFlow/integration/ProductsTableMoveIT.java`: o script `docker/mysql/migrations/us4-products.sql` move todas as linhas de `orderflow.products` para `catalog.products`, e a `V1` do serviço não faz nada depois
- [ ] T066 [US4] Mover com `git mv` o pacote `product` para `services/catalog/src/main/java/com/matheus/orderFlow/product/`; copiar `JpaAuditingConfig.java`; criar `services/catalog/src/main/resources/application.yml` (schema `catalog`, usuário `catalog`, `public-get-paths: /products, /products/*`, `jwk-set-uri`, rastreamento) e `services/catalog/src/main/resources/db/migration/V1__create_products_table.sql` com `CREATE TABLE IF NOT EXISTS products` na forma de hoje
- [ ] T067 [US4] Criar `docker/mysql/migrations/us4-products.sql` com `RENAME TABLE orderflow.products TO catalog.products`, até `ProductsTableMoveIT` passar
- [ ] T068 [US4] Mover `ProductIT`, `ProductControllerTest` e `ProductServiceTest` para `services/catalog/src/test/java/com/matheus/orderFlow/`, com base de testes própria usando `TestTokens`; descrição da API em `services/catalog/src/main/java/com/matheus/orderFlow/product/OpenApiConfig.java`
- [ ] T069 [US4] Tirar produtos do monólito: pacote `product`, limpeza de `products` em `AbstractIntegrationTest.java`, `public-get-paths` de `monolith/src/main/resources/application.yml`, seção de produtos de `OpenApiConfig.java`; `CartIT` e `OrderIT` passam a usar o catálogo do WireMock
- [ ] T070 [US4] Rota `/products/**` para `${CATALOG_URL:http://catalog:8080}` e entrada na documentação em `gateway/src/main/resources/application.yml`, com o caso em `GatewayRoutingIT.java`; serviço `catalog` em `docker-compose.yml` e `CATALOG_URL` no `monolith`
- [ ] T071 [P] [US4] Criar `e2e/src/test/java/com/matheus/orderFlow/e2e/CatalogOutageE2E.java`: vitrine pela porta 8080 (cenário 1); com `catalog` parado, a finalização responde 503 em `ErrorResponse` em até 5 s, `GET /cart` não muda e `GET /orders` funciona (cenários 2 e 3, SC-003)
- [ ] T072 [US4] Atualizar `README.md`: catálogo como serviço, a primeira chamada síncrona, timeout e circuit breaker e o motivo, contagem de testes

**Checkpoint**: catálogo fora do monólito; falha do catálogo isolada e clara.

---

## Phase 7: User Story 5 - Carrinho como serviço próprio (Priority: P5)

**Goal**: o carrinho sai do monólito, e o checkout atravessa dois serviços sem
duplicar pedido.

**Independent Test**: `CheckoutIT` com `OrderIdempotencyIT`, e `CheckoutE2E`.

### Tests for User Story 5 ⚠️

- [ ] T073 [P] [US5] Criar `monolith/src/test/java/com/matheus/orderFlow/integration/OrderIdempotencyIT.java`: mesma `Idempotency-Key` do mesmo usuário responde 201 com o mesmo `Location` e o mesmo corpo, e existe um só pedido; mesma chave de outro usuário cria outro pedido; sem o cabeçalho, comportamento de hoje; chave com mais de 64 caracteres responde 400 ([contracts/http-internal.md](contracts/http-internal.md))
### Implementation for User Story 5

- [ ] T074 [US5] Criar `monolith/src/main/resources/db/migration/V9__add_idempotency_key_to_orders.sql` com `idempotency_key VARCHAR(64) NULL` e `UNIQUE (user_id, idempotency_key)`; campo `idempotencyKey` ("`VARCHAR(64)`, nulo", "Única junto com `user_id`") em `monolith/src/main/java/com/matheus/orderFlow/order/Order.java`
- [ ] T075 [US5] Aceitar o cabeçalho `Idempotency-Key` ("até 64 caracteres") em `monolith/src/main/java/com/matheus/orderFlow/order/OrderController.java` e devolver o pedido já criado para a chave repetida em `OrderService.java`, inclusive quando duas criações concorrentes batem na restrição única, até `OrderIdempotencyIT` passar
- [ ] T076 [US5] Criar `services/cart/pom.xml` (`platform-web`, `platform-security`, Spring Data Redis, `resilience4j-spring-boot3`, springdoc, `spring-boot-starter-zipkin`; testes com `platform-test`, WireMock e Testcontainers Redis) e `services/cart/src/main/java/com/matheus/orderFlow/CartApplication.java`; registrar o módulo
- [ ] T077 [P] [US5] Escrever primeiro `services/cart/src/test/java/com/matheus/orderFlow/integration/CheckoutIT.java` com WireMock no lugar de `catalog` e `orders`: resposta da criação do pedido atrasada além de "3,5 s" faz a finalização responder 503 com o carrinho intacto; a repetição envia a mesma `Idempotency-Key`; o sucesso esvazia o carrinho e apaga a chave; `orders` fora do ar responde 503 com o carrinho intacto (cenário 3); o `Authorization` chega aos dois serviços
- [ ] T078 [US5] Mover com `git mv` o pacote `cart` para `services/cart/src/main/java/com/matheus/orderFlow/cart/`; acrescentar `checkoutKey` ("texto, nulo") a `Cart.java`; criar `CatalogClient.java` e `CatalogProduct.java` (`id`, `name`, `price`) e `OrdersClient.java` (`POST /orders` com `Idempotency-Key`, timeout de 3,5 s), todos com `BearerTokenPropagationInterceptor` e circuit breaker com fallback para `ServiceUnavailableException`
- [ ] T079 [US5] Em `services/cart/src/main/java/com/matheus/orderFlow/cart/CartService.java`, gerar e gravar a `checkoutKey` antes de chamar o `orders`, reaproveitá-la nas repetições, e apagar carrinho e chave só depois do sucesso; criar `services/cart/src/main/resources/application.yml` (Redis com usuário `cart`, `CATALOG_URL`, `ORDERS_URL`, instâncias `catalog` e `orders` do Resilience4j, `jwk-set-uri`, rastreamento), até `CheckoutIT` passar
- [ ] T080 [US5] Mover `CartIT` e `CartTest` para `services/cart/src/test/java/com/matheus/orderFlow/`, com base de testes própria (Redis, WireMock, `TestTokens`); descrição da API em `services/cart/src/main/java/com/matheus/orderFlow/cart/OpenApiConfig.java`
- [ ] T081 [US5] Usuários de ACL no Redis: em `docker-compose.yml`, `redis-server` com o usuário `monolith` restrito a `~refreshToken*`, o usuário `cart` restrito a `~cart*` e o usuário padrão desligado, com senhas por variável de ambiente; `SPRING_DATA_REDIS_USERNAME` e `SPRING_DATA_REDIS_PASSWORD` no `monolith` e no `cart`
- [ ] T082 [US5] Tirar o carrinho do monólito: pacote `cart`, limpeza de chaves do carrinho em `AbstractIntegrationTest.java` e seção de carrinho de `OpenApiConfig.java`; o pacote `monolith/src/main/java/com/matheus/orderFlow/catalog/` fica, porque o pedido ainda usa o `CatalogClient`
- [ ] T083 [US5] Rota `/cart/**` para `${CART_URL:http://cart:8080}` e entrada na documentação em `gateway/src/main/resources/application.yml`, com o caso em `GatewayRoutingIT.java`; serviço `cart` em `docker-compose.yml`
- [ ] T084 [P] [US5] Criar `e2e/src/test/java/com/matheus/orderFlow/e2e/CheckoutE2E.java`: a finalização pela porta 8080 cria o pedido e esvazia o carrinho (cenário 1); com `monolith` parado, 503 em até 5 s e carrinho intacto (cenário 3); com `cart` parado, `GET /orders` funciona (cenário 4)
- [ ] T085 [US5] Atualizar `README.md`: carrinho como serviço, a cadeia do checkout, a chave de idempotência e o motivo, contagem de testes

**Checkpoint**: checkout atravessando `cart`, `monolith` e `catalog`, sem pedido duplicado.

---

## Phase 8: User Story 6 - Identidade como serviço próprio (Priority: P6)

**Goal**: cadastro, login e credenciais saem do monólito.

**Independent Test**: `IdentityE2E` ([quickstart](quickstart.md), cenário 10).

### Implementation for User Story 6

- [ ] T086 [US6] Criar `services/identity/pom.xml` (`platform-web`, `platform-security`, Spring Data JPA, Flyway, MySQL, Spring Data Redis, springdoc, `spring-boot-starter-zipkin`; testes com Testcontainers MySQL e Redis) e `services/identity/src/main/java/com/matheus/orderFlow/IdentityApplication.java`; registrar o módulo
- [ ] T087 [P] [US6] Escrever primeiro `services/identity/src/test/java/com/matheus/orderFlow/integration/UsersTableMoveIT.java`: o script `docker/mysql/migrations/us6-users.sql` move todas as linhas de `orderflow.users` para `identity.users`, e a `V1` do serviço não faz nada depois
- [ ] T088 [US6] Mover com `git mv` o pacote `user` (com `TokenService`, refresh tokens, `JwtKeyConfig`, `IssuerConfig`, `JwksController` e `AdminBootstrap`) para `services/identity/src/main/java/com/matheus/orderFlow/user/`; criar `services/identity/src/main/resources/application.yml` (schema `identity`, usuário `identity`, Redis com usuário `identity` restrito a `~refreshToken*`, `public-paths: /auth/**, /.well-known/jwks.json`, administrador inicial e chave privada por variável de ambiente) e `db/migration/V1__create_users_table.sql` com `CREATE TABLE IF NOT EXISTS users` na forma de hoje
- [ ] T089 [US6] Criar `docker/mysql/migrations/us6-users.sql` com `RENAME TABLE orderflow.users TO identity.users`, até `UsersTableMoveIT` passar
- [ ] T090 [US6] Mover `UserIT`, `RefreshTokenIT`, `SecurityIT`, `JwksIT`, `AdminBootstrapTest`, `AuthControllerTest`, `UserControllerTest`, `UserServiceTest`, `UserTest` e `TokenServiceTest` para `services/identity/src/test/java/com/matheus/orderFlow/`, com base de testes própria; descrição da API em `services/identity/src/main/java/com/matheus/orderFlow/user/OpenApiConfig.java`
- [ ] T091 [US6] Tirar a identidade do monólito: pacote `user`, `JwtDecoder` local (passa a usar `jwk-set-uri` do `identity`), limpeza de `users` e de refresh tokens em `AbstractIntegrationTest.java` (os testes do monólito passam a usar `TestTokens`), `public-paths` de `monolith/src/main/resources/application.yml`, seção de contas de `OpenApiConfig.java`
- [ ] T092 [US6] Rotas `/auth/**` e `/users/**` para `${IDENTITY_URL:http://identity:8080}` e entrada na documentação em `gateway/src/main/resources/application.yml`, com os casos em `GatewayRoutingIT.java`; serviço `identity` em `docker-compose.yml`; `JWK_SET_URI` apontando para o `identity` em todos os serviços; usuário `monolith` do Redis removido e usuário `identity` criado
- [ ] T093 [P] [US6] Criar `e2e/src/test/java/com/matheus/orderFlow/e2e/IdentityE2E.java`: cadastro, login, renovação com detecção de reuso e logout pela porta 8080 (cenário 1); com `identity` parado, um token válido continua aceito por `catalog`, `cart` e `monolith` (cenário 2); email inexistente e senha errada respondem igual (cenário 3)
- [ ] T094 [US6] Atualizar `README.md`: identidade como serviço, o cache das chaves públicas e o motivo, contagem de testes

**Checkpoint**: só a identidade emite credenciais; o monólito só verifica.

---

## Phase 9: User Story 7 - Pedidos como serviço próprio (Priority: P7)

**Goal**: o que sobra do monólito vira o serviço de pedidos, e o sistema único deixa de existir.

**Independent Test**: `FullPurchaseE2E` ([quickstart](quickstart.md), cenários 3 a 9).

### Implementation for User Story 7

- [ ] T095 [US7] Renomear o módulo com `git mv monolith services/orders`; `OrderFlowApplication.java` vira `OrdersApplication.java`; atualizar `<modules>` em `pom.xml`, `MODULE=services/orders` em `docker-compose.yml` e as referências em `.github/workflows/ci.yml`
- [ ] T096 [P] [US7] Escrever primeiro `services/orders/src/test/java/com/matheus/orderFlow/integration/OrdersTablesMoveIT.java`: o script `docker/mysql/migrations/us7-orders.sql` move todas as linhas de `orders`, `order_items` e `outbox_messages` de `orderflow` para `orders`, e a `V1` do serviço não faz nada depois
- [ ] T097 [US7] Trocar as migrações de `services/orders/src/main/resources/db/migration/` por `V1__create_orders_tables.sql` com `CREATE TABLE IF NOT EXISTS` para `orders` (com `user_id`, `version` e `idempotency_key` "`VARCHAR(64)`, nulo" com `UNIQUE (user_id, idempotency_key)`), `order_items` e `outbox_messages` (com `traceparent` "`VARCHAR(55)`, nulo"); datasource no schema `orders` com usuário `orders`
- [ ] T098 [US7] Criar `docker/mysql/migrations/us7-orders.sql` com `RENAME TABLE` de `orders`, `order_items` e `outbox_messages` de `orderflow` para `orders`, até `OrdersTablesMoveIT` passar
- [ ] T099 [US7] No gateway, remover a rota padrão e rotear `/orders/**` para `${ORDERS_URL:http://orders:8080}`, mantendo `/internal/**` bloqueado, em `gateway/src/main/resources/application.yml`, com os casos em `GatewayRoutingIT.java`; em `docker-compose.yml`, o serviço `monolith` vira `orders` e `ORDERS_URL` aponta para ele em `cart` e `sales`; em `docker/mysql/init/01-schemas.sql`, o usuário `orderflow` sai
- [ ] T100 [US7] Descrição da API do serviço de pedidos em `services/orders/src/main/java/com/matheus/orderFlow/order/OpenApiConfig.java`, substituindo o `OpenApiConfig.java` do antigo monólito
- [ ] T101 [P] [US7] Criar `e2e/src/test/java/com/matheus/orderFlow/e2e/FullPurchaseE2E.java`: cadastro, vitrine, carrinho, finalização, confirmação, cobrança, envio, entrega e relatório pela porta 8080, sem o monólito (cenário 1, SC-008)
- [ ] T102 [P] [US7] Criar `e2e/src/test/java/com/matheus/orderFlow/e2e/OrderFactsDeliveryE2E.java`: com `payment` e `sales` parados durante as mudanças de status, os dois recebem todos os fatos ao subir, cada um com efeito único (cenário 2, SC-005)
- [ ] T103 [US7] Atualizar `README.md`: fim do monólito, o script de movimentação dos pedidos, contagem de testes

**Checkpoint**: todas as capacidades são serviços; o sistema único não existe mais.

---

## Phase 10: Polish & Cross-Cutting Concerns

**Purpose**: critérios que atravessam todas as etapas e limpeza da transição.

- [ ] T104 [P] Criar `e2e/src/test/java/com/matheus/orderFlow/e2e/FailureIsolationE2E.java`: com `payment`, `sales` e `notifications` parados ao mesmo tempo, criar, confirmar, enviar, entregar e cancelar pedidos funciona (SC-002)
- [ ] T105 [P] Criar `e2e/src/test/java/com/matheus/orderFlow/e2e/TraceE2E.java`: pela API do Zipkin (`/api/v2/traces`), o rastro de um checkout mostra `gateway`, `cart`, `orders` e `catalog`; com `catalog` parado, o passo do catálogo aparece com erro; o rastro de uma confirmação chega aos consumidores de `payment` e `sales` (FR-017, SC-010)
- [ ] T106 [P] Criar `contracts/src/test/java/com/matheus/orderFlow/contracts/order/FactCompatibilityTest.java`: um consumidor compilado com a versão anterior de cada fato processa o fato com campos acrescentados (SC-011)
- [ ] T107 Remover os ids legados de `contracts/src/main/java/com/matheus/orderFlow/contracts/order/OrderFacts.java` depois de confirmar que nenhuma fila, nem a DLQ, guarda mensagem com nome de classe no `__TypeId__`
- [ ] T108 [P] Revisar a descrição da API de cada serviço (`OpenApiConfig.java`) e a página única de documentação do gateway, como pedem os padrões de trabalho da constituição
- [ ] T109 Atualizar `README.md`: tabela de serviços, dados e portas, desenho da arquitetura, resumo das decisões com link para [research.md](research.md), contagem de testes por módulo e roteiro
- [ ] T110 Executar os cenários 1 a 10 de [quickstart.md](quickstart.md) e confirmar `./mvnw verify` e `./mvnw verify -Pe2e` verdes no CI

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: começa já; T001 depende do merge da adoção do Spec Kit em `main`.
- **Foundational (Phase 2)**: depende do Setup; bloqueia todas as user stories.
- **US1 (Phase 3)**: depende da Foundational; é a base de todas as outras.
- **US2 a US7**: dependem da US1 e seguem a ordem de prioridade.
- **Polish**: depende das user stories desejadas; T104 depende também da spec 002.

### User Story Dependencies

| Story | Depende de | Por quê |
|---|---|---|
| US1 | Foundational | entrada única e RS256 são pré-requisito de todo serviço |
| US2 | US1 | usa `platform-test`, `jwk-set-uri` e a rota do gateway |
| US3 | US1 | idem; independente da US2 |
| US4 | US1, US2 | reaproveita o repasse de credencial e o 503 criados na US2 |
| US5 | US4 | o `cart` chama o `catalog` como serviço |
| US6 | US1 | troca o emissor; depois da US5 para que o carrinho já valide por JWKS |
| US7 | US2 a US6 | o monólito só vira `orders` quando nada mais depende dele por dentro |

A spec 002 (notificações) pode ser implementada a qualquer momento depois da US1.

### Within Each User Story

- Testes escritos e falhando antes da implementação.
- Módulo e classe de aplicação antes de mover o código; código antes dos testes movidos.
- Script de movimentação de dados antes de tirar a funcionalidade do monólito.
- Rota do gateway e Compose por último, e então o `e2e`.
- `./mvnw verify` verde e README em dia ao fim de cada fase.

### Parallel Opportunities

- Setup: T005 e T006.
- Foundational: T009 e T014 em paralelo com T008 a T013.
- US1: T021, T022, T023 e T026; depois T032, T033 e T034.
- US2: T036, T037 e T038.
- US3 e US2 podem correr em paralelo depois da US1, por pessoas diferentes.
- Em cada story, o teste de movimentação de dados e o teste `e2e` são [P].

---

## Parallel Example: User Story 1

```bash
# Testes da credencial juntos:
Task: "JwksIT em monolith/src/test/java/com/matheus/orderFlow/integration/JwksIT.java"
Task: "Casos de chave estranha e HS256 em monolith/src/test/java/com/matheus/orderFlow/integration/SecurityIT.java"
Task: "Refresh sem senha em monolith/src/test/java/com/matheus/orderFlow/integration/RefreshTokenIT.java"

# Testes ponta a ponta juntos, depois do Compose:
Task: "RoutesThroughGatewayE2E em e2e/src/test/java/com/matheus/orderFlow/e2e/"
Task: "DirectCallWithoutCredentialE2E em e2e/src/test/java/com/matheus/orderFlow/e2e/"
Task: "DocumentationE2E em e2e/src/test/java/com/matheus/orderFlow/e2e/"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Setup: multi-módulo com o `monolith` idêntico ao de hoje.
2. Foundational: bibliotecas, fatos versionados e rastreamento.
3. US1: entrada única e credencial RS256.
4. **Parar e validar**: cenários 3, 7 e 10 do quickstart.

### Incremental Delivery

1. Cada user story é um PR, com `verify` e `e2e` verdes.
2. Depois da US1, a spec 002 (notificações) entra como primeiro serviço novo.
3. US2 a US7, uma por vez, cada uma tirando um pacote do monólito.

---

## Notes

- [P] = arquivos diferentes, sem dependência pendente.
- O rótulo [Story] liga a tarefa à user story da spec.
- Nomes de propriedades do gateway, dos clientes HTTP do Boot 4 e da classe de
  Compose do Testcontainers são conferidos no classpath antes do uso.
- Commit ao fim de cada tarefa ou grupo lógico, em Conventional Commits em
  português, sem trailer de co-autoria.
