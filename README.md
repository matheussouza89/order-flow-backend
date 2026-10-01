# OrderFlow

[![CI](https://github.com/matheussouza89/order-flow-backend/actions/workflows/ci.yml/badge.svg)](https://github.com/matheussouza89/order-flow-backend/actions/workflows/ci.yml)

API REST para gestão de pedidos, construída em Spring Boot 4 e Java 21.

O projeto é um estudo de arquitetura em evolução: cada decisão de desenho está
documentada abaixo, com o motivo por trás dela. O fluxo de compra está completo
de ponta a ponta — **catálogo**, **carrinho**, **pedido** e **pagamento** —,
incluindo o ciclo de vida do pedido e a cobrança assíncrona com política de
resiliência; autenticação e paginação são os próximos passos
(ver [Roadmap](#roadmap)).

---

## Stack

| Camada | Tecnologia |
|---|---|
| Linguagem | Java 21 |
| Framework | Spring Boot 4.1.1 (Web MVC, Data JPA, Validation) |
| Banco | MySQL 8.4 + Flyway |
| Mensageria | RabbitMQ |
| Dado efêmero | Redis (carrinho, com TTL) |
| Documentação | springdoc-openapi (Swagger UI) |
| Resiliência | Resilience4j |
| Testes | JUnit 5, Mockito, Testcontainers, WireMock, Awaitility |
| Build | Maven |

---

## Como rodar

**Pré-requisito:** Docker.

Suba o projeto inteiro — API, MySQL, Redis e RabbitMQ:

```bash
docker compose up --build
```

A imagem da aplicação é construída pelo Dockerfile em dois estágios: o
primeiro compila com Maven, o segundo leva apenas o jar para uma imagem com
JRE. As conexões vêm de variáveis de ambiente, com o ambiente local como
padrão — por isso a mesma imagem serve para qualquer ambiente.

Para desenvolver com a aplicação fora do container (Java 21 necessário), suba
só a infraestrutura e rode pelo Maven:

```bash
docker compose up -d mysql redis rabbitmq
```

```bash
./mvnw spring-boot:run
```

| Recurso | URL |
|---|---|
| API | http://localhost:8080/products |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Health check | http://localhost:8080/actuator/health |

---

## Como testar

A suíte é separada por velocidade, para não pagar o custo do Docker a cada
alteração:

```bash
./mvnw test
```

Roda os **115 testes unitários** em poucos segundos, sem Docker.

```bash
./mvnw verify
```

Roda os unitários **mais os 34 de integração**, que sobem MySQL, Redis e
RabbitMQ reais via Testcontainers, e um WireMock fazendo as vezes do gateway de
pagamento.

A separação é feita por convenção de nome: o Surefire pega `*Test.java`, o
Failsafe pega `*IT.java`.

---

## Decisões de arquitetura

### Organização por funcionalidade, não por camada

```
com.matheus.orderFlow/
├── product/          Product, ProductService, ProductRepository, ProductController, DTOs
├── cart/             Cart, CartService, CartRepository, CartController, DTOs
├── order/            Order, OrderItem, OrderStatus, OrderService, OrderController, DTOs
├── payment/          Payment, PaymentService, PaymentProcessor, gateway e listener, DTOs
└── shared/
    ├── config/       configuração da aplicação
    ├── messaging/    exchange e conversor compartilhados
    └── exception/    exceções e tratamento global
```

O padrão mais comum em tutoriais Spring é agrupar por camada (`controller/`,
`service/`, `repository/`). O problema aparece na terceira entidade: para mexer
em "produto" você abre quatro pastas, e cada pasta mistura assuntos que não
conversam entre si.

Aqui o critério é **o que muda junto fica junto**.

O ganho concreto vem do encapsulamento que isso permite:

| Classe | Visibilidade |
|---|---|
| `ProductService` | `public` — porta de entrada do domínio |
| `ProductDto` / `ProductResponse` | `public` — contrato de entrada e saída |
| `Product` | package-private |
| `ProductRepository` | package-private |
| `ProductController` | package-private |

A entidade JPA e o repositório são **inalcançáveis** de fora do pacote. Nenhum
serviço futuro consegue acessar a persistência direto ou alterar um produto
contornando as regras de negócio — o compilador garante a fronteira.

### Validação no domínio, uma fonte de verdade

A entidade `Product` valida as próprias invariantes no construtor e no
`update()`, lançando `DomainValidationException` com o campo que falhou:

```java
Product(String name, String description, BigDecimal price) {
    validate(name, description, price);
    ...
}
```

Como não existe outro caminho para construir ou alterar um produto, **é
impossível ter uma instância inválida em memória**.

A alternativa comum — Bean Validation (`@NotBlank`, `@Positive`) na DTO — foi
descartada de propósito. Ela só protege o que entra via HTTP: um consumer de
fila, um importador ou um seeder passariam por fora. Manter as duas seria duas
fontes de verdade para a mesma regra, livres para divergir.

### Entidade separada do contrato HTTP

Os endpoints devolvem `ProductResponse`, nunca a entidade. Assim:

- adicionar um campo interno em `Product` não altera a API
- renomear uma coluna não quebra clientes
- o JSON é uma lista explícita de campos, e não um espelho acidental da tabela

### Respostas de erro com formato único

Todo erro tratado sai no mesmo formato, venha de validação de domínio, de
formato ou de JSON malformado:

```json
{
  "status": 400,
  "message": "price: Product price cannot be negative",
  "errors": { "price": "Product price cannot be negative" }
}
```

O `GlobalExceptionHandler` também registra em log com níveis distintos: `debug`
para 404 (operação normal), `warn` para validação (padrões de uso incorreto) e
`error` com stack trace para falhas não previstas (bug a investigar).

A validação de domínio acontece em transação: criar um pedido grava várias
linhas, e um item inválido no meio não pode deixar um pedido órfão no banco.

### Pedido guarda um retrato do catálogo, não uma referência

O `OrderItem` copia `productName` e `unitPrice` no momento da compra, em vez de
apontar para o produto:

```
order_items
  product_id    ← referência, sem chave estrangeira
  product_name  ← cópia
  unit_price    ← cópia
```

Parece duplicação, mas são fatos diferentes: *"o teclado custa R$ 100"* é o
catálogo hoje; *"neste pedido o teclado foi vendido por R$ 100"* é um acordo do
passado. Mudar o preço não pode reescrever o histórico de vendas — um pedido é
um documento fiscal, não uma consulta viva.

A consequência é a ausência de chave estrangeira para `products`. Com ela,
apagar um produto já vendido seria impossível, e o acoplamento entre os
agregados voltaria pela camada do banco. Sem ela, o pedido continua legível
mesmo que o produto deixe de existir, e a integridade é garantida na aplicação:
o `OrderService` consulta o `ProductService`, que lança 404 se o produto não
existir.

O carrinho é o caso oposto, e está logo abaixo.

### Carrinho reflete o catálogo ao vivo; o pedido congela

O carrinho guarda apenas `productId → quantidade`. Nome, preço e total são
buscados no catálogo a cada leitura:

```
carrinho   produto + quantidade        → preço de hoje, lido do catálogo
pedido     produto + quantidade
           + nome + preço unitário     → cópia, congelada no checkout
```

É a mesma pergunta respondida de dois jeitos, porque são momentos diferentes.
Um carrinho aberto há três dias precisa mostrar o preço de hoje — exibir o
preço antigo e cobrar outro no checkout seria pior do que atualizar. Já o
pedido é um acordo fechado: o preço que valeu é o do instante da compra.

O checkout é a fronteira entre os dois. Ele cria o pedido a partir do carrinho
— é ali que o preço deixa de ser consulta e vira cópia — e só então descarta o
carrinho. A ordem importa: se o pedido falhar, o carrinho continua de pé para o
cliente tentar de novo.

Como o carrinho só aponta para o catálogo, um produto removido não pode
invalidá-lo. O item some da resposta e o total é recalculado sem ele, em vez de
o carrinho inteiro quebrar por causa de uma linha.

### Carrinho no Redis, não no MySQL

Carrinho é rascunho: a maioria é abandonada e nenhum deles precisa sobreviver
para auditoria. Guardá-lo no banco relacional significaria duas tabelas, duas
migrations e uma rotina de limpeza para lixo que ninguém vai consultar.

No Redis ele é uma chave com **TTL de 7 dias, renovado a cada alteração** — o
abandono se resolve sozinho, sem rotina de limpeza. E a estrutura é um mapa
simples, sem o relacionamento pai-filho que o modelo relacional exigiria.

A diferença prática em relação ao JPA aparece no código: não há *dirty
checking*, então toda alteração termina com um `save` explícito. E o Redis não
armazena hash vazio — um carrinho cuja última linha foi removida volta da
leitura com o mapa nulo, não vazio, o que o domínio trata ao carregar.

### Agregados com fronteira garantida pelo compilador

`Order` e `OrderItem` formam um agregado: vivem no mesmo pacote e se relacionam
por `@OneToMany`, com a `Order` como única porta de entrada — ela vincula os
itens a si mesma e recalcula o total, e nenhum dos dois é acessível de fora.

Já `Product` é outro agregado, em outro pacote e package-private. Isso torna
*impossível* declarar `@ManyToOne Product` dentro de `OrderItem`: a referência
entre agregados é por `UUID`, e o dado vem pelo `ProductService`.

O que normalmente é convenção de equipe aqui é regra que o compilador cobra.

### Ciclo de vida do pedido como máquina de estados

O pedido avança por ações de negócio, não por atribuição de status:

```
PENDING ──→ CONFIRMED ──→ SHIPPED ──→ DELIVERED
   │            │
   └────────────┴──→ CANCELLED
```

| Ação | Permitida a partir de |
|---|---|
| `confirm` | `PENDING` |
| `ship` | `CONFIRMED` |
| `deliver` | `SHIPPED` |
| `cancel` | `PENDING`, `CONFIRMED` |

Não existe um `PATCH /orders/{id}/status` recebendo o novo valor. O cliente
solicita uma **ação** e o domínio decide o resultado — caso contrário, bastaria
enviar `DELIVERED` para pular o fluxo inteiro. Cada transição é um método da
entidade que valida o estado atual antes de mudar.

Transição inválida responde **409 Conflict**, não 400: a requisição está
correta e o pedido é válido; o que impede a operação é o estado atual do
recurso. Por isso o corpo desse erro não traz `errors` — não há campo enviado
pelo cliente a que apontar.

### Cobrança assíncrona, fora da requisição do usuário

Confirmar um pedido publica um evento; o pagamento é criado ao consumi-lo. Duas
consequências: a confirmação responde de imediato, sem esperar o gateway, e o
gateway fora do ar não impede o pedido de ser confirmado.

O evento é publicado com `@TransactionalEventListener` em `AFTER_COMMIT`.
Mensagem não tem rollback: publicar dentro da transação faria o consumidor
reagir a uma confirmação que um erro posterior desfez.

O RabbitMQ entrega **pelo menos uma vez**, então mensagem repetida é
comportamento normal, não anomalia. A chave de idempotência é derivada do
pedido, de modo que a mesma mensagem produz a mesma chave: a verificação evita
o trabalho e a constraint única protege contra entregas simultâneas.

Cada consumidor declara a própria fila. A configuração compartilhada tem apenas
a exchange e o conversor, então acrescentar um consumidor não mexe no que já
existe — e quem publica continua sem saber quem escuta.

### Chamada ao gateway fora da transação

Segurar uma conexão do banco enquanto se espera um terceiro esgota o pool sob
carga. Por isso a cobrança acontece em três etapas:

```
[transação]  grava o pagamento como PENDING
[sem transação]  chama o gateway
[transação]  grava APPROVED, DECLINED ou FAILED
```

O pagamento fica visível como `PENDING` no intervalo — se a aplicação morrer no
meio, existe registro de que a cobrança foi iniciada.

A orquestração vive numa classe separada das transações porque `@Transactional`
funciona por proxy: um método transacional chamado de dentro da própria classe
não abre transação alguma, sem erro nem aviso.

### Resiliência na integração com o gateway

| Padrão | Papel |
|---|---|
| **Timeout** | desiste de esperar, em vez de prender a thread |
| **Retry** | 3 tentativas com backoff exponencial, só para falhas transitórias |
| **Circuit breaker** | para de tentar quando o gateway está comprovadamente fora |
| **Idempotência** | a chave vai no cabeçalho, para que retentativa não vire segunda cobrança |

O retry só cobre exceções passageiras — timeout e 5xx. Uma requisição malformada
falharia igual nas três tentativas, e retentar seria desperdício.

Recusa e falha são estados distintos, porque pedem reações distintas: `DECLINED`
é resposta do gateway e não adianta repetir; `FAILED` é falha técnica e pode ser
reprocessado.

Os testes sobem um WireMock em porta aleatória e exercitam cada caminho,
incluindo o mais difícil de demonstrar — com o circuito aberto, a asserção é que
**nenhuma requisição chegou ao gateway**.

### Testes de integração com infraestrutura real

Os testes unitários cobrem regras e casos de borda com tudo mockado. Os de
integração cobrem o que **só um banco real prova**: geração e persistência do
UUID como `BINARY(16)`, execução das migrations, preenchimento dos campos de
auditoria e os limites das colunas.

A configuração fica em `AbstractIntegrationTest`, e não copiada em cada classe.
Isso não é só conveniência: sem o `@Import` dos containers, um teste cairia na
configuração do `application.yml` e o *clean-up* apagaria o banco de
desenvolvimento local.

---

## Endpoints

**Produtos**

| Método | Rota | Status | Descrição |
|---|---|---|---|
| `GET` | `/products` | 200 | Lista todos os produtos |
| `GET` | `/products/{id}` | 200 / 404 | Busca por id |
| `POST` | `/products` | 201 + `Location` | Cria um produto |
| `PUT` | `/products/{id}` | 200 / 404 | Atualiza um produto |
| `DELETE` | `/products/{id}` | 204 / 404 | Remove um produto |

**Carrinho**

| Método | Rota | Status | Descrição |
|---|---|---|---|
| `GET` | `/carts/{cartId}` | 200 / 404 | Busca o carrinho com o preço atual |
| `POST` | `/carts/{cartId}/items` | 200 / 400 / 404 | Soma à quantidade; cria o carrinho no primeiro item |
| `PUT` | `/carts/{cartId}/items/{productId}` | 200 / 400 / 404 | Substitui a quantidade; zero remove |
| `DELETE` | `/carts/{cartId}/items/{productId}` | 200 / 404 | Remove o item |
| `POST` | `/carts/{cartId}/checkout` | 200 / 400 / 404 | Gera o pedido e descarta o carrinho |

Não há rota para criar um carrinho: ele nasce no primeiro item e morre no
checkout ou no fim do TTL. Um `POST /carts` vazio só criaria chave para ser
abandonada.

**Pedidos**

| Método | Rota | Status | Descrição |
|---|---|---|---|
| `GET` | `/orders` | 200 | Lista todos os pedidos |
| `GET` | `/orders/{id}` | 200 / 404 | Busca por id |
| `POST` | `/orders` | 201 + `Location` | Cria um pedido |
| `POST` | `/orders/{id}/confirm` | 200 / 404 / 409 | Confirma o pedido |
| `POST` | `/orders/{id}/ship` | 200 / 404 / 409 | Marca como enviado |
| `POST` | `/orders/{id}/deliver` | 200 / 404 / 409 | Marca como entregue |
| `POST` | `/orders/{id}/cancel` | 200 / 404 / 409 | Cancela o pedido |

**Pagamentos**

| Método | Rota | Status | Descrição |
|---|---|---|---|
| `GET` | `/payments/{id}` | 200 / 404 | Busca por id |
| `GET` | `/payments/orders/{orderId}` | 200 / 404 | Busca o pagamento de um pedido |

Não há rota para criar, aprovar ou recusar um pagamento: o valor vem do pedido e
o resultado vem do gateway.

O corpo do `POST /orders` envia apenas o que o cliente pode decidir:

```json
{
  "items": [
    { "productId": "3f2a8c11-...", "quantity": 2 }
  ]
}
```

Nome, preço e total vêm do catálogo e do domínio — nunca do cliente.

---

## Roadmap

**Concluído**

- [x] Domínio de produtos com validação encapsulada na entidade
- [x] Domínio de pedidos com itens, cálculo de total e retrato do catálogo
- [x] Ciclo de vida do pedido com transições validadas no domínio
- [x] Tratamento global de erros com formato único e logging por severidade
- [x] Organização por funcionalidade com entidade e repositório encapsulados
- [x] Cobrança assíncrona por evento, com consumidor idempotente
- [x] Integração com gateway usando timeout, retry, circuit breaker e idempotência
- [x] Carrinho no Redis com TTL, preço ao vivo e checkout gerando o pedido
- [x] 149 testes, separados por velocidade (unitários e integração)
- [x] Pipeline de CI rodando `mvn verify` a cada push
- [x] Imagem da aplicação e stack completa em Docker, com conexões por variável de ambiente
- [x] Documentação OpenAPI

**Próximos passos**

- [ ] Paginação nas listagens
- [ ] Autenticação com Spring Security + JWT
