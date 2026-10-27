# Research: Separação em microsserviços

Decisões técnicas da feature [003](spec.md), no formato do Spec Kit. Pela
constituição 1.1.0, este arquivo é o registro das decisões difíceis de desfazer.

Versões conferidas em 2026-10-22: Spring Boot 4.1.1 (parent do projeto), Spring
Cloud 2025.1.3 (start.spring.io: compatível com Boot `>=4.0.0` e `<4.2.0-M1`),
Spring Cloud Gateway 5.0.3, Spring AMQP 4.1.1, springdoc-openapi 3.1.1,
Resilience4j 2.3.0, Testcontainers 2.0.5. Em 2026-10-24: Gatling 3.16.0 e
`gatling-maven-plugin` 4.21.12.

## R0. Separar em microsserviços

- **Decisão**: separar o sistema em serviços, um de cada vez, a partir do monólito,
  na ordem das user stories da spec.
- **Motivo**: o objetivo é aprender e demonstrar arquitetura distribuída num
  projeto de portfólio. Não há problema de escala, de equipe ou de cadência de
  deploy que exija a separação. As fronteiras já existem: entidade, repositório e
  controller são package-private, e agregados se referenciam por `UUID`, sem
  chave estrangeira entre eles.
- **Alternativas consideradas**:
  - Monólito modular com Spring Modulith, verificando fronteiras e eventos sem
    rede. Custa muito menos e é a alternativa mais forte; não atende o FR-002
    (implantar cada capacidade sozinha). Se o custo de manter vários serviços
    travar a evolução do projeto, esta decisão deve ser revista em favor dela.
  - Extrair só notificações e relatórios: pouco custo, mas não exercita chamada
    síncrona entre serviços, entrada única nem credencial distribuída.
  - Reescrever tudo de uma vez: não há estado intermediário que funcione, e
    contradiz o FR-013.

## R1. Serviços e dependências

- **Decisão**:

  | Serviço | Capacidade | Dados | Depende de |
  |---|---|---|---|
  | `identity` | contas, login, credenciais | MySQL, Redis | — |
  | `catalog` | produtos | MySQL | — |
  | `cart` | carrinho | Redis | `catalog`, `orders` (HTTP) |
  | `orders` | pedidos | MySQL, com outbox | `catalog` (HTTP) |
  | `payment` | cobrança | MySQL | fatos de pedido |
  | `sales` | relatórios de vendas | MongoDB | fatos de pedido; `orders` só na reconstrução |
  | `notifications` | e-mails de status | spec 002 | fatos de pedido e de usuário |
  | `gateway` | entrada única | — | todos, por rota |

- **Motivo**: segue o mapa de dependências do código atual. `user` e `product` não
  dependem de outra funcionalidade; `payment` e `sales` só consomem fatos;
  `cart` e `order` são os acoplamentos síncronos. Carrinho e pedidos ficam
  separados por decisão da spec: o carrinho é efêmero e de escrita frequente, o
  pedido é durável e transacional.
- **Alternativas consideradas**: carrinho e pedidos no mesmo serviço, que
  manteria o checkout num processo só. Descartada na spec.

## R2. Organização do repositório

- **Decisão**: um repositório, Maven multi-módulo. Módulos: `platform/platform-web`,
  `platform/platform-security`, `platform/platform-messaging`,
  `platform/platform-outbox`, `platform/platform-test`, `contracts`, `gateway`,
  `services/<serviço>`, `monolith` (temporário), `e2e` e `load`. O `platform` é
  dividido para que cada serviço dependa só do que usa: o gateway não pode
  carregar o resource server, e serviços sem JPA ou sem RabbitMQ não podem
  carregar o outbox ou a mensageria. Com módulos separados, a única configuração
  condicional é o `JwtDecoder` do `platform-security`, registrado como
  auto-configuração com `@ConditionalOnMissingBean`, porque o emissor valida com
  a própria chave até a US6. O `platform` não conhece domínio (princípio V): o
  `platform-web` só tem exceções genéricas por status (404, 400, 409, 401 e 503),
  e as exceções de uma funcionalidade ficam no serviço, estendendo a genérica; o
  `platform-messaging` não depende de `contracts` e recebe de cada serviço o mapa
  de ids de tipo.
- **Motivo**: um build, um CI e um histórico; o portfólio mostra a arquitetura
  inteira num lugar. O Maven já é a ferramenta do projeto.
- **Alternativas consideradas**: um repositório por serviço, que exige versionar e
  publicar `platform` e `contracts` como artefatos; Gradle, que troca a ferramenta
  sem ganho para o objetivo.

## R3. Entrada única

- **Decisão**: Spring Cloud Gateway Server WebMVC
  (`spring-cloud-starter-gateway-server-webmvc` 5.0.3, BOM do Spring Cloud
  2025.1.3), roteando por prefixo. Durante a migração, a rota padrão vai para o
  monólito, e cada etapa move um prefixo para o serviço novo.
- **Motivo**: mesmo modelo servlet do resto do projeto, configuração por
  propriedades, e testável com as mesmas ferramentas. Os nomes das propriedades de
  rota devem ser conferidos no classpath na implementação, porque mudaram entre
  versões do gateway.
- **Alternativas consideradas**: gateway WebFlux, que traz um segundo modelo de
  programação; nginx ou Traefik, que não produzem o `ErrorResponse` nem rodam nos
  testes Java; nenhuma entrada, que viola o FR-001.

## R4. Erros na entrada única

- **Decisão**: quando o serviço de destino não responde, o gateway devolve
  `ErrorResponse`: 503 quando a conexão falha e 504 quando o tempo se esgota. A
  mesma regra vale para o serviço que chama outro: o `cart` devolve 503 em
  `ErrorResponse` quando o `orders` ou o `catalog` falham.
- **Motivo**: o FR-001 e o princípio VI pedem um formato de erro só. Sem tratamento,
  o cliente receberia a página de erro padrão do gateway.
- **Alternativas consideradas**: deixar o erro padrão do gateway (quebra o formato);
  responder sempre 503 (esconde a diferença entre indisponível e lento).

## R5. Credencial de acesso

- **Decisão**: o emissor assina com RS256 e publica as chaves públicas em
  `GET /.well-known/jwks.json`. Os outros serviços validam com
  `jwk-set-uri`. A chave privada vem de configuração; quando não há chave
  configurada (desenvolvimento e testes), o emissor gera um par ao subir. O
  cabeçalho leva `kid`. Até a User Story 6, o emissor é o monólito. Claims mantidas:
  `iss` (`orderflow`), `sub` (id do usuário), `roles`, `iat`, `exp`.
- **Motivo**: com HS256, todo serviço que valida tem o segredo e poderia emitir
  credenciais (FR-004). Gerar a chave ao subir evita versionar chave privada, o
  que o GitGuardian já apontou com os padrões de desenvolvimento. A troca não exige
  novo login (FR-011): o refresh token é opaco e guardado no Redis, então quem tem
  um token de acesso HS256 recebe 401 e renova sem senha.
- **Alternativas consideradas**: manter HS256 (viola o FR-004); Spring
  Authorization Server (servidor OAuth2 completo, muito além do necessário);
  aceitar HS256 e RS256 juntos por 15 minutos (mais código para uma janela que o
  refresh já cobre).

## R6. Chamadas entre serviços

- **Decisão**: interfaces HTTP declarativas do Spring Framework 7
  (`@HttpExchange`) sobre `RestClient`, com URL base e timeouts por configuração.
  O `RestClient` sai do `RestClient.Builder` do Boot, que já aplica a observação
  do rastreamento; um cliente criado à mão não propaga o `traceparent`. Um
  interceptor em `platform` repassa o cabeçalho `Authorization` da requisição
  recebida (FR-014). Circuit breaker do Resilience4j, já usado no gateway de
  pagamento, com `ignore-exceptions: HttpClientErrorException` e fallback
  declarado só para falha de conexão, tempo esgotado, 5xx e circuito aberto. O
  `ignore-exceptions` só tira a exceção da contagem, e o fallback é escolhido pelo
  tipo da exceção: um fallback genérico transformaria um 4xx em 503. Timeouts:
  1,5 s por chamada ao `catalog` e 3,5 s do `cart` para o `orders`. URLs por DNS
  do Docker Compose, sem descoberta de serviços. Os nomes das propriedades dos
  clientes HTTP do Boot 4 devem ser conferidos no classpath.
- **Prazo da finalização**: a finalização consulta o `catalog` uma vez por item
  no `cart`, antes de criar o pedido, e de novo no `orders`, ao criá-lo. A soma
  cresce com o número de itens e pode passar dos 4,5 s da entrada única, que então
  responde 504 enquanto o `cart` segue, cria o pedido e esvazia o carrinho: o
  cliente vê erro com o pedido criado. Por isso a finalização no `cart` tem prazo
  próprio de 4 s, contado do início. O `cart` não chama o `orders` se o prazo já
  passou e não apaga o carrinho se ele passou durante a chamada. Nos dois casos
  responde 503 e guarda a `checkoutKey`, e a repetição devolve o mesmo pedido, se
  ele chegou a ser criado. Se a entrada responder 504 antes, o estado é o mesmo. A
  entrada garante a resposta em até 4,5 s, abaixo dos 5 s do SC-003.
- **Motivo**: a biblioteca já resolve cliente, timeout e circuit breaker
  (princípio II). Com a execução restrita à máquina local e ao CI, o DNS do Compose
  basta.
- **Alternativas consideradas**: OpenFeign (dependência a mais, em manutenção);
  Eureka ou Consul (descoberta sem necessidade no escopo local); credencial do
  próprio serviço (opção B da clarificação, descartada). Para o prazo: timeouts
  menores (a soma continua crescendo com os itens); uma consulta só ao `catalog`
  com todos os ids (rota nova e mudança maior no catálogo); tirar a pré-checagem
  do `cart` (o produto indisponível passaria a responder como o `POST /orders`, e
  não com o 409 de hoje, contra o FR-001).
- **Limite conhecido**: entre a US4 e a US5, a finalização ainda roda inteira no
  `monolith`, sem chave de idempotência. Com o catálogo lento, mas abaixo do
  timeout, num carrinho com muitos itens, a entrada pode responder 504 depois de o
  pedido ser criado, e a repetição encontra o carrinho vazio. A US5 fecha a janela
  com o prazo e a `checkoutKey`.

## R7. Checkout sem pedido duplicado

- **Decisão**: na primeira tentativa de finalizar, o `cart` gera uma chave e a guarda
  no próprio carrinho. A chave vai no cabeçalho `Idempotency-Key` de
  `POST /orders`. O `orders` guarda a chave, única por usuário, e devolve o pedido
  já criado quando ela se repete. A chave some com o carrinho depois do sucesso.
- **Motivo**: cobre a perda da resposta depois da criação (FR-009, SC-004) sem
  mudar a API. O projeto já usa chave de idempotência nos pagamentos.
- **Alternativas consideradas**: deduplicar pelo conteúdo do carrinho (dois pedidos
  legítimos iguais virariam um); checkout assíncrono com 202 e consulta posterior
  (muda a API e viola o FR-001).

## R8. Dados por serviço

- **Decisão**: um servidor MySQL com um schema e um usuário por serviço, cada
  usuário com permissão só no próprio schema. Cada serviço tem seu Flyway, com a
  primeira migração em `CREATE TABLE IF NOT EXISTS`. Na etapa de extração, um
  script operacional em `docker/mysql/migrations/` move as tabelas com
  `RENAME TABLE orderflow.x TO <schema>.x`, que é atômico e não copia dados. O
  script roda uma vez, como root, nos volumes que já têm dados; ambientes novos
  recebem as tabelas pela `V1` do serviço. Não é uma migração do Flyway do
  monólito porque, nos testes, o usuário do monólito só tem permissão no próprio
  schema e não consegue criar nem renomear para outro. No Redis, um usuário de ACL por
  serviço, restrito ao prefixo das chaves (`refreshToken*` para `identity`,
  `cart*` para `cart`).
- **Motivo**: o FR-003 fica garantido por permissão, não por convenção. O
  `RENAME TABLE` preserva os dados sem cópia (FR-012); a parada curta durante a
  etapa é aceita pelas premissas.
- **Alternativas consideradas**: um servidor de banco por serviço (Compose pesado
  sem ganho no escopo local); um schema com permissão por tabela (dono das
  tabelas fica implícito); cópia de dados por script (mais código e risco de
  divergência).

## R9. Fatos publicados

- **Decisão**: mantém a exchange `orderflow.events`, e cada consumidor declara a
  própria fila, como hoje. A DLX e a DLQ compartilhadas são declaradas pelo
  `platform`. O tipo da mensagem passa a ser um id lógico com versão
  (`order.confirmed.v1`), mapeado pelo `DefaultJacksonJavaTypeMapper`, que aceita
  também os nomes de classe antigos durante a transição. O mapa vem de um bean
  `FactTypes` que cada serviço declara a partir de `contracts`, para que o
  `platform-messaging` não conheça os fatos. Uma versão nova de
  formato ganha id e chave de roteamento próprios, publicada junto com a antiga
  até os consumidores migrarem (FR-018).
- **Motivo**: com o nome da classe Java no cabeçalho, mover um evento de pacote
  quebra os consumidores. O `JacksonJsonMessageConverter` do Spring AMQP 4.1.1
  desliga `FAIL_ON_UNKNOWN_PROPERTIES` (conferido no bytecode), então o consumidor
  já ignora informação nova (FR-019); um teste garante isso (SC-011).
- **Alternativas consideradas**: manter o nome da classe como tipo (acopla
  consumidores ao pacote de quem publica); registro de schemas com Avro (pesado
  para o escopo).

## R10. Outbox por serviço

- **Decisão**: o outbox genérico de hoje (`shared/outbox`, sem domínio) vai para o
  `platform`, e cada serviço que publica tem a própria tabela. A tabela ganha a
  coluna `traceparent`; o relay restaura o contexto do rastro ao publicar.
- **Motivo**: sem o contexto guardado, o rastro termina no outbox, porque a
  publicação acontece depois, em outra thread (FR-016).
- **Alternativas consideradas**: publicar dentro da transação (perde a garantia que
  justificou o outbox); captura de mudanças do banco com Debezium (infraestrutura
  pesada para o escopo).

## R11. Rastreamento

- **Decisão**: `spring-boot-starter-zipkin` 4.1.1 (ponte Brave e envio ao Zipkin)
  no gateway e em todos os serviços, com um container do Zipkin no Compose.
  Amostragem total em desenvolvimento e no CI. Observação ligada no `RabbitTemplate`
  e nos listeners, para o rastro atravessar os fatos. O id do rastro entra no
  padrão de log.
- **Motivo**: é o caminho mais curto no Boot 4.1 para o FR-016 e o FR-017: a
  instrumentação de controllers, clientes HTTP e RabbitMQ vem por configuração.
- **Alternativas consideradas**: `spring-boot-starter-opentelemetry` com coletor
  OTLP e Jaeger ou Tempo (mais containers; bom candidato para produção); só um id
  nos logs, sem painel (opção B da clarificação, descartada).

## R12. Reconstrução dos relatórios

- **Decisão**: o `orders` expõe `GET /internal/orders/export`, paginado e restrito
  a `ADMIN`, com itens, status e versão. O `sales` expõe
  `POST /admin/sales/rebuild`, restrito a `ADMIN`, que repassa a credencial do
  administrador e grava com a mesma guarda de versão de hoje. A flag de
  inicialização `--orderflow.views.rebuild=true` deixa de existir.
- **Motivo**: a reconstrução de hoje lê `orders`, `order_items` e
  `outbox_messages` direto no banco (viola o FR-003) e roda na inicialização, sem
  usuário (incompatível com o FR-014). As duas rotas novas acrescentam, sem mudar
  as existentes.
- **Alternativas consideradas**: o `orders` republicar retratos dos pedidos por
  mensagem (mais peças móveis para o mesmo resultado); manter a leitura do banco
  (viola o FR-003).

## R13. Documentação única

- **Decisão**: cada serviço expõe o próprio `/v3/api-docs` com springdoc 3.1.1, e o
  gateway serve a interface do Swagger com uma entrada por serviço
  (`springdoc.swagger-ui.urls`), buscando os documentos pela própria entrada única.
- **Motivo**: uma página só, com seletor de serviço (FR-020), sem ferramenta de
  fusão de documentos.
- **Alternativas consideradas**: fundir tudo num documento OpenAPI (exige
  ferramenta e conflita nomes de schema); página de links (opção C da
  clarificação, descartada).

## R14. Empacotamento e execução

- **Decisão**: um `Dockerfile` com `ARG MODULE` constrói qualquer serviço. O
  `docker compose up --build` sobe a infraestrutura (MySQL, Redis, RabbitMQ,
  MongoDB, WireMock, Zipkin), o gateway e os serviços (FR-015). Só o gateway
  publica a porta 8080; os serviços ficam na rede interna.
- **Motivo**: um arquivo em vez de oito iguais; a porta única reforça a entrada
  única.
- **Alternativas consideradas**: buildpacks por módulo (imagens base grandes e
  lentas no CI); um `Dockerfile` por serviço (duplicação).

## R15. Testes

- **Decisão**: os testes de hoje (`*Test` e `*IT`) acompanham o código para o
  módulo de cada serviço, cada um com seu Testcontainers. O `contracts` testa os
  fatos, incluindo consumidor da versão anterior (SC-011). Um módulo `e2e` novo,
  com testes `*E2E` no Failsafe e perfil Maven `e2e`, sobe o Compose pelo
  Testcontainers e prova os critérios que atravessam serviços: SC-001 (todas as
  rotas pela entrada única), SC-002, SC-003, SC-004, SC-005, SC-009, SC-010 e
  SC-012. No CI, `verify` roda em todos os módulos, e o `e2e` roda num job
  separado. A classe de Compose do Testcontainers 2.0.5 deve ser conferida no
  classpath.
- **Motivo**: isolamento de falha e entrada única só se provam com os serviços
  rodando juntos. O SC-001 cita a suíte existente, mas ela roda dentro do processo
  com MockMvc; a cobertura das rotas pela entrada única fica no `e2e`.
- **Alternativas consideradas**: só testes por serviço (não provam os SCs de
  isolamento); testes de contrato com Pact (pesado para fatos com formato
  simples).

## R16. Monólito durante a migração

- **Decisão**: a aplicação atual vira o módulo `monolith`, sem mudar pacotes. Cada
  etapa tira um pacote dele e troca chamadas internas por clientes HTTP: o
  `ProductService` vira cliente do `catalog` na User Story 4; o `OrderService`
  usado pelo carrinho vira cliente do `orders` na User Story 5. Na User Story 7, o
  módulo `monolith` passa a ser `services/orders`.
- **Motivo**: o código e os testes que já funcionam seguem funcionando, e cada
  etapa é um passo pequeno (FR-013).
- **Alternativas consideradas**: escrever o serviço de pedidos do zero
  (reimplementa código testado).

## R17. Pacotes

- **Decisão**: cada serviço mantém o pacote da funcionalidade de hoje
  (`com.matheus.orderFlow.product`, `...cart`, `...order`). A classe de aplicação
  de cada serviço fica em `com.matheus.orderFlow` (`CatalogApplication`,
  `CartApplication` e assim por diante). O `platform` mantém
  `com.matheus.orderFlow.shared`; o `contracts` usa
  `com.matheus.orderFlow.contracts`.
- **Motivo**: mover sem renomear preserva o histórico do git e os testes. Com a
  classe de aplicação no pacote raiz, o component scan e a varredura de entidades
  alcançam os pacotes do `platform` sem auto-configuração. A regra de
  visibilidade (princípio V) continua valendo dentro de cada serviço.
- **Alternativas consideradas**: classe de aplicação no pacote da funcionalidade,
  que exigiria auto-configuração em cada módulo do `platform`; um pacote raiz novo
  por serviço, que renomeia tudo sem ganho.

## R18. Teste de carga

- **Decisão**: Gatling 3.16.0 com a DSL em Java, num módulo `load` com o
  `gatling-maven-plugin` 4.21.12. Uma simulação, `PurchaseSimulation`, cria os
  produtos e os usuários antes da carga e roda 50 clientes simultâneos por 5
  minutos pela entrada única: vitrine, carrinho, finalização e consulta de
  pedidos. As asserções da simulação são o SC-013 e o SC-014, então o build falha
  quando a meta não é atingida. A linha de base é medida depois da US1 (monólito
  atrás do gateway) e a medição final no fim da migração, com o mesmo cenário,
  para isolar o custo da separação (SC-015). No CI, um workflow manual roda a
  simulação com carga reduzida e só verifica a taxa de falhas: as asserções de
  tempo são desligadas por `-DcheckLatency=false`, porque o runner compartilhado
  não dá tempos comparáveis.
- **Motivo**: fica no mesmo ecossistema Java e Maven do projeto, as asserções
  transformam as metas em teste, e o relatório HTML serve de evidência no
  portfólio. Medir antes e depois, na mesma máquina, é o que dá sentido aos
  números de uma execução local.
- **Alternativas consideradas**: k6 (bom e leve, mas traz JavaScript ao
  projeto); JMeter (plano em XML e interface gráfica, pesado de manter);
  rodar a carga no CI a cada PR (os runners compartilhados variam demais para
  medir latência, e a memória já é um risco do plano).

## Pendências

Nenhuma. A meta de tempo de resposta na operação normal, pendente depois da
clarificação, foi definida no SC-013 e no SC-014 (R18).
