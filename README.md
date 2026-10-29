# OrderFlow

[![CI](https://github.com/matheussouza89/order-flow-backend/actions/workflows/ci.yml/badge.svg)](https://github.com/matheussouza89/order-flow-backend/actions/workflows/ci.yml)

API REST para gestão de pedidos, construída em Spring Boot 4 e Java 21.

O projeto é um estudo de arquitetura em evolução: cada decisão de desenho está
documentada abaixo, com o motivo por trás dela. O fluxo de compra está completo
de ponta a ponta — **catálogo**, **carrinho**, **pedido** e **pagamento** —,
incluindo o ciclo de vida do pedido e a cobrança assíncrona com política de
resiliência, com autenticação por JWT, rotas protegidas por papel e recursos
vinculados ao usuário do token (ver [Roadmap](#roadmap)).

Está em andamento a separação em microsserviços, especificada em
[specs/003-microsservicos](specs/003-microsservicos/spec.md). A primeira etapa
está pronta: o cliente entra por um gateway, a credencial é verificável por
qualquer serviço sem que ele consiga emiti-la, e o rastro de uma requisição
atravessa serviços e mensagens. O sistema ainda roda num monólito atrás do
gateway, que perde uma capacidade por etapa.

---

## Stack

| Camada | Tecnologia |
|---|---|
| Linguagem | Java 21 |
| Framework | Spring Boot 4.1.1 (Web MVC, Data JPA, Validation) |
| Banco | MySQL 8.4 + Flyway |
| Mensageria | RabbitMQ |
| Dado efêmero | Redis (carrinho e refresh tokens, com TTL) |
| Leitura de vendas | MongoDB (projeção de pedidos, com Spring Data MongoDB) |
| Entrada única | Spring Cloud Gateway Server WebMVC |
| Documentação | springdoc-openapi (Swagger UI) |
| Resiliência | Resilience4j |
| Segurança | Spring Security + JWT RS256 com JWKS (oauth2-resource-server) |
| Rastreamento | Micrometer Tracing (Brave) + Zipkin |
| Testes | JUnit 5, Mockito, Testcontainers, WireMock, Awaitility, Gatling |
| Build | Maven multi-módulo |

---

## Como rodar

**Pré-requisito:** Docker.

Suba o projeto inteiro — gateway, monólito, MySQL, MongoDB, Redis, RabbitMQ,
WireMock e Zipkin:

```bash
docker compose up --build
```

Só o gateway publica a porta 8080. O monólito fica na rede interna, e uma
chamada direta a ele sem credencial é recusada como qualquer outra.

Um único Dockerfile constrói qualquer módulo, escolhido pelo argumento `MODULE`.
O primeiro estágio compila com Maven só o módulo e as bibliotecas de que ele
depende; o segundo leva apenas o jar para uma imagem com JRE. As conexões vêm de
variáveis de ambiente, com o ambiente local como padrão, então a mesma imagem
serve para qualquer ambiente.

⚠️ Três variáveis não devem usar o padrão fora da máquina local:

| Variável | Por quê |
|---|---|
| `JWT_PRIVATE_KEY` | chave privada RSA, em PEM PKCS#8, que assina os tokens. Sem ela, uma chave é gerada na subida: serve para desenvolvimento, mas cada reinício invalida os tokens de acesso já emitidos (o refresh continua valendo) |
| `ADMIN_EMAIL` | identifica o administrador criado na primeira subida |
| `ADMIN_PASSWORD` | a senha dele; com o padrão, o log avisa na subida |

O administrador é criado automaticamente quando não existe nenhum, então subir
o projeto já dá acesso às rotas de `ADMIN`.

Cada serviço terá schema e usuário próprios no MySQL, criados por
`docker/mysql/init/01-schemas.sql` na primeira subida do volume. Um volume que
já existia antes da separação não roda esse script de novo; aplique uma vez:

```bash
docker compose exec -T mysql mysql -uroot -proot < docker/mysql/init/01-schemas.sql
```

Para desenvolver com o monólito fora do container (Java 21 necessário), suba
só a infraestrutura e rode o módulo pelo Maven. Ele atende direto na porta 8080:

```bash
docker compose up -d mysql mongo redis rabbitmq zipkin
```

```bash
./mvnw -pl monolith spring-boot:run
```

| Recurso | URL |
|---|---|
| API | http://localhost:8080/products |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Health check | http://localhost:8080/actuator/health |
| Rastros (Zipkin) | http://localhost:9411 |

---

## Como testar

A suíte é separada por velocidade, para não pagar o custo do Docker a cada
alteração:

```bash
./mvnw test
```

Roda os **250 testes unitários** em poucos segundos, sem Docker.

```bash
./mvnw verify
```

Roda os unitários **mais os 162 de integração**, que sobem MySQL, MongoDB, Redis e
RabbitMQ reais via Testcontainers, e um WireMock fazendo as vezes do gateway de
pagamento ou do serviço atrás da entrada única.

```bash
./mvnw verify -Pe2e -pl e2e -am
```

Roda os **10 testes ponta a ponta**, que constroem as imagens, sobem o Compose
inteiro pelo Testcontainers e falam com o sistema pela porta 8080, como um
cliente. São eles que provam o que só aparece com os serviços juntos: todas as
rotas respondendo pela entrada única, a chamada direta sem credencial recusada e
a documentação única cobrindo todas as rotas. Usam as mesmas portas do Compose,
então a pilha de desenvolvimento precisa estar parada.

| Módulo | Unitários | Integração | Ponta a ponta |
|---|---|---|---|
| `monolith` | 219 | 142 | — |
| `gateway` | — | 20 | — |
| `platform-outbox` | 16 | — | — |
| `contracts` | 7 | — | — |
| `platform-messaging` | 5 | — | — |
| `platform-test` | 2 | — | — |
| `platform-web` | 1 | — | — |
| `e2e` | — | — | 10 |

A separação é feita por convenção de nome: o Surefire pega só `*Test.java`, o
Failsafe pega `*IT.java`. O teste que sobe o contexto inteiro se chama
`OrderFlowApplicationIT` porque precisa dos containers. Com o nome antigo,
`OrderFlowApplicationTests`, ele rodava no Surefire, que por padrão aceita
`*Tests`, e o `./mvnw test` deixava de rodar sem Docker.

---

## Teste de carga

A separação em serviços acrescenta saltos de rede a cada requisição. Para saber
quanto isso custa, o mesmo teste de carga roda depois da primeira etapa, como
linha de base, e no fim da migração, na mesma máquina. Os números servem para
comparar as duas medições, não como capacidade de produção.

Com o Compose de pé:

```bash
./mvnw -pl load gatling:test
```

A simulação, em Gatling, cria 20 produtos e um usuário por cliente simulado.
Depois, 50 clientes entram ao longo de 30 segundos e, por 5 minutos, repetem o
mesmo ciclo: vitrine (lista e detalhe), item no carrinho, finalização da compra,
lista e detalhe dos pedidos, com 0,5 a 1,5 segundo de pausa entre os passos. O
login acontece uma vez por cliente, fora do ciclo: o BCrypt custa cerca de 250 ms
de CPU por login e dominaria a medição sem dizer nada sobre a separação. Número de
clientes e duração mudam com `-Dusers` e `-Dduration` (em segundos).

O build falha se uma meta não for atingida: p95 da finalização até 1 s, p95 da
vitrine até 300 ms e menos de 1% de falhas. O relatório HTML fica em
`load/target/gatling/`.

**Linha de base**, em 2026-10-29, com o monólito atrás do gateway:

| Requisição | Total | p50 (ms) | p75 (ms) | p95 (ms) | p99 (ms) | Máx. (ms) |
|---|---|---|---|---|---|---|
| vitrine: lista de produtos | 3.718 | 7 | 8 | 11 | 17 | 52 |
| vitrine: detalhe do produto | 3.718 | 5 | 5 | 8 | 12 | 23 |
| item no carrinho | 3.708 | 9 | 10 | 13 | 20 | 65 |
| finalização da compra | 3.692 | 13 | 15 | 22 | 40 | 85 |
| lista de pedidos | 3.686 | 7 | 8 | 10 | 14 | 34 |
| detalhe do pedido | 3.686 | 5 | 6 | 9 | 13 | 24 |
| login | 50 | 247 | 254 | 264 | 285 | 285 |
| **todas** | **22.258** | **7** | **10** | **15** | **23** | **285** |

Vazão média de 67 requisições por segundo, sem nenhuma falha. Máquina: AMD
Ryzen 7 5700G (8 núcleos, 16 threads), 31 GB de RAM, Windows 11 com Docker
Desktop 25.0.3 sobre WSL2 (16 CPUs e 15 GB para os containers). Cada aplicação
roda com `-Xmx256m`.

---

## Decisões de arquitetura

### Organização por funcionalidade, não por camada

```
com.matheus.orderFlow/
├── user/             User, UserService, UserRepository, AuthController, UserController, DTOs
├── product/          Product, ProductService, ProductRepository, ProductController, DTOs
├── cart/             Cart, CartService, CartRepository, CartController, DTOs
├── order/            Order, OrderItem, OrderStatus, OrderService, OrderController, DTOs
├── payment/          Payment, PaymentService, PaymentProcessor, gateway e listener, DTOs
├── sales/            projeção de pedidos em MongoDB e relatórios de vendas
└── shared/
    ├── config/       configuração da aplicação
    ├── messaging/    exchange e conversor compartilhados
    ├── outbox/       evento gravado em transação e publicado pelo agendador
    ├── web/          PageResponse, envelope das listagens
    ├── security/     cadeia de filtros, tokens de acesso e refresh, hash de senha
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

No checkout a regra se inverte: o item removido não é ignorado, e a operação é
recusada com **409**, listando todos os produtos indisponíveis de uma vez.
Esconder o item na leitura é conveniência; esconder no checkout seria entregar
ao cliente um pedido diferente do que ele conferiu, e é o único momento do fluxo
em que o valor vira cobrança. O carrinho continua intacto — quem decide o que
fazer com o item é o cliente.

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

O evento não é enviado durante a requisição: ele é gravado junto com o pedido e
publicado depois, pelo mecanismo descrito [logo abaixo](#o-evento-é-gravado-não-publicado).

O RabbitMQ entrega **pelo menos uma vez**, então mensagem repetida é
comportamento normal, não anomalia. A chave de idempotência é derivada do
pedido, de modo que a mesma mensagem produz a mesma chave: a verificação evita
o trabalho e a constraint única protege contra entregas simultâneas.

Cada consumidor declara a própria fila. A configuração compartilhada tem apenas
a exchange e o conversor, então acrescentar um consumidor não mexe no que já
existe — e quem publica continua sem saber quem escuta.

### O evento é gravado, não publicado

Publicar em `AFTER_COMMIT` evita o pior erro — um consumidor reagindo a algo que
um rollback desfez. Mas sobra uma janela: entre o commit e o envio, se a
aplicação morrer ou o RabbitMQ estiver fora, o pedido fica **CONFIRMED sem
cobrança alguma**. E em silêncio, porque ninguém falhou.

A raiz é que são dois sistemas e não existe transação que cubra os dois. Mudar a
ordem só troca qual lado fica inconsistente.

A saída é não publicar nada durante a requisição. O evento vira uma linha numa
tabela, gravada **na mesma transação do pedido**:

```
[transação]   status = CONFIRMED
              INSERT INTO outbox_messages
              commit ✓          ← um banco só, atômico de verdade

[agendador]   lê pendentes → publica → marca como publicada
```

Com RabbitMQ fora, a linha espera e sai quando ele voltar. Isso inverte uma
decisão anterior: o listener do evento passou de `@TransactionalEventListener`
em `AFTER_COMMIT` para `@EventListener` comum, porque agora queremos que ele
rode **dentro** da transação. A gravação no mesmo banco resolve o que a ordem de
execução só contornava.

O preço é entrega repetida: se o envio funcionar e a marcação falhar, a mensagem
sai duas vezes. O consumidor já era idempotente — a chave deriva do pedido —, o
que é pré-requisito para adotar o padrão, não detalhe.

A mensagem guarda o JSON já serializado e o nome da classe, não o objeto. Assim
o pacote do outbox não conhece nenhum evento concreto, e a mensagem sai com o
mesmo formato de antes: o consumidor não soube da mudança.

A busca das pendentes usa `SKIP LOCKED`. Com mais de uma instância, cada
agendador pega um lote diferente em vez de disputarem as mesmas linhas.

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

### Senha nunca é armazenada, e a role não vem do cliente

A senha vai para o banco só como hash BCrypt — função de mão única, com sal
embutido e propositalmente lenta. A lentidão é a característica, não o defeito:
com um banco vazado, ela é o que separa "testar milhões de combinações por
segundo" de "testar algumas por segundo".

O sal explica um detalhe visível nos testes: dois usuários com a mesma senha
produzem hashes **diferentes**. Sem ele, hashes iguais entregariam que duas
contas compartilham a senha.

O hash é feito por um `PasswordEncoder` injetado, e não pela biblioteca chamada
direto na service. Assim o algoritmo é decisão de configuração — trocar BCrypt
por Argon2 não toca em regra de negócio — e o login usa exatamente o mesmo
encoder para conferir.

O custo do hash vem de propriedade e cai nos testes. Com o padrão de produção,
cada usuário criado custaria cerca de 300ms, e a suíte de integração pagaria
isso dezenas de vezes.

A **role não existe no corpo da requisição**. Aceitá-la deixaria qualquer um se
cadastrar como administrador — é a mesma regra que mantém preço, total e status
fora do payload do pedido: o que o domínio decide, o cliente não envia.

### Senha validada antes de virar hash

A entidade guarda `passwordHash` e nunca vê a senha. Isso parece detalhe de
nome, mas resolve uma armadilha: uma validação de senha dentro do construtor
estaria checando um hash de 60 caracteres, e regras como "mínimo de 8" ou
"precisa de número" passariam sempre, sem significar nada.

Por isso a política é um método estático, chamado pela service **antes** de
hashear — o único momento em que a senha existe em claro. A regra continua no
domínio, aplicada onde faz sentido.

O mesmo cuidado vale para as operações: existem `changeName`, `changeEmail` e
`changePasswordHash`, não um `update` com todos os campos. Com o método único,
renomear alguém obrigava a service a carregar o hash da senha de um lado para o
outro — e um erro ali reescreveria a credencial numa operação que não deveria
tocá-la.

### Token assinado, sem estado no servidor

O login devolve um JWT de acesso; as demais rotas o exigem no cabeçalho
`Authorization`. O servidor não guarda quem está logado — o token carrega a
identidade, assinada, e qualquer instância valida sozinha com a chave.

Isso tem um preço que vale registrar: **token assinado não se cancela**. Emitiu,
vale até expirar, mesmo que o usuário seja apagado no minuto seguinte. É o que
se paga por não guardar estado, e é o motivo de ele durar quinze minutos — o
[par com o refresh](#dois-tokens-porque-são-dois-problemas-opostos), logo
abaixo, é o que transforma isso em revogação utilizável.

O conteúdo do token é codificado, **não criptografado**: qualquer um que o
tenha consegue ler. A assinatura prova que ninguém alterou, não esconde. Por
isso ele leva apenas o id do usuário e a role — nunca nome, email ou qualquer
dado pessoal, e há teste decodificando o payload para garantir.

A validação fica a cargo do `oauth2-resource-server`, e não de um filtro
escrito à mão. O motivo é concreto: um validador ingênuo lê do próprio token
qual algoritmo usar, e aceita um token forjado que declara `"alg": "none"`. É
uma falha que passa em todos os testes de caminho feliz. O algoritmo aqui é
fixado na configuração e o que o token afirma é ignorado.

A assinatura é RS256, não HS256. Com HS256, o segredo que valida é o mesmo que
assina: separado em serviços, cada serviço que verifica tokens poderia emitir um
token de administrador. Com RS256, só o emissor tem a chave privada, e os demais
validam com a pública, publicada em `GET /.well-known/jwks.json`. Essa rota fica
na rede interna; a entrada única não a repassa. O cabeçalho leva `kid`, a
impressão digital da chave, para que uma troca de chave seja reconhecida.

A troca de algoritmo não obrigou ninguém a entrar de novo. O token de acesso
antigo, HS256, passou a receber 401, e o refresh token, que é opaco e guardado
no Redis, gera um par novo sem pedir senha. Aceitar os dois algoritmos por quinze
minutos seria código a mais para uma janela que o refresh já cobre.

### O primeiro administrador nasce fora do cadastro

Promover alguém a `ADMIN` exige ser `ADMIN` — e no banco vazio não existe
nenhum. Alguém precisa nascer fora do fluxo normal.

A aplicação resolve isso na subida: se não houver nenhum administrador, cria um
com as credenciais vindas de variável de ambiente. Havendo, não faz nada —
reiniciar não multiplica administradores.

A alternativa óbvia seria um `INSERT` numa migration, e ela tem dois problemas.
O menor é que migration roda **uma vez**: se o administrador for apagado, ela
não recria, porque o Flyway já a marcou como aplicada — e "criar se não houver"
é verificação de execução, não de versionamento. O maior é a credencial: o hash
no arquivo não protege nada se a senha em claro precisa estar no README ou na
cabeça de alguém para servir.

Se o email configurado já pertencer a um usuário comum, nada acontece: promover
alguém por configuração seria escalada de privilégio silenciosa. E sem
configuração a aplicação sobe assim mesmo, apenas avisando — falhar quebraria
quem só quer rodar na própria máquina.

### Virar administrador tem um nome, não um parâmetro

O construtor de `User` fixa o papel em `USER`. Criar um administrador exige uma
fábrica nomeada:

```java
static User administrator(String name, String email, String passwordHash)
```

Aceitar a role como parâmetro reabriria o buraco pelo lado de dentro: qualquer
caminho futuro — um importador, um seeder, um endpoint novo — poderia criar um
administrador por engano, ou repassando algo que veio do cliente.

Com a fábrica, só quem escreve o nome dela faz isso, e um `grep` encontra todos
os lugares. É a mesma ideia de `confirm()` em vez de `setStatus()`: a operação
tem nome, e o nome é auditável.

### Recurso alheio responde 404, não 403

Pedido, carrinho e pagamento pertencem a quem os criou. Pedir o de outro
responde **404**, como se não existisse.

Um 403 seria mais literal — "existe, mas não é seu" — e é justamente o problema:
ele confirma que aquele identificador existe. Varrendo ids, dá para contar
quantos pedidos a loja tem, ou descobrir se uma compra específica aconteceu.
Quando a própria existência do recurso é informação, negar é dizer menos do que
esconder.

A regra não cabe em anotação. `@PostAuthorize` roda **depois** do método — numa
escrita, o efeito já aconteceu — e devolve 403, o que anula o ponto acima. Por
isso o dono é verificado em código na service, e a listagem filtra na consulta
em vez de carregar tudo e descartar.

O carrinho resolve isso de forma mais forte: o `cartId` saiu da URL e vem do
token. Não há identificador alheio a tentar, porque não há identificador.

A conta é a exceção que confirma o critério: `/users/{id}` responde **403**, não
404. Quem monta essa URL já conhece o id — ele vem do `Location` do cadastro e é
o *subject* do próprio token —, então não há existência a esconder. O 404 serve
contra enumeração; onde não há o que enumerar, ele só confunde.

Aqui a regra cabe em anotação, porque depende apenas do id recebido e do token,
sem precisar carregar o recurso:

```java
@PreAuthorize("#id.toString() == authentication.name or hasRole('ADMIN')")
```

Sem ela, qualquer usuário autenticado trocava a senha de qualquer outro — e
isso é pior do que ler um pedido alheio: não expõe um dado, entrega a conta.

### Regra de papel na anotação, regra de dono no código

As regras de papel vivem em `@PreAuthorize` nas controllers, não na cadeia de
filtros. Na cadeia elas dependeriam da ordem das linhas — `/products/**` depois
de `GET /products` funciona; antes, fecha a vitrine — e cresceriam num arquivo
distante do endpoint que governam.

A cadeia fica com o que é mesmo transversal: rota pública e o piso
`anyRequest().authenticated()`.

Já a anotação fica na **controller**, e não na service, por um motivo concreto
deste projeto: o `PaymentService` é chamado pelo consumidor do RabbitMQ, onde
não existe usuário autenticado. Um `@PreAuthorize` ali faria o consumidor
falhar em produção, longe da causa. O que precisa valer nas duas portas de
entrada é código comum.

Um efeito colateral que vale saber: 403 negado na cadeia passa pelo
`accessDeniedHandler`, mas negado no `@PreAuthorize` sobe até o
`GlobalExceptionHandler`. São caminhos diferentes para o mesmo status, e os
dois precisam existir.

### Confirmar é do cliente, despachar é da operação

```
PENDING ──confirm──→ CONFIRMED ──ship──→ SHIPPED ──deliver──→ DELIVERED
          cliente                ADMIN            ADMIN
```

A máquina de estados já existia; faltava dizer **quem** aciona cada transição.
Sem isso, o cliente marcava o próprio pedido como entregue — ele nunca despachou
nada. `confirm` e `cancel` são decisões de compra; `ship` e `deliver` registram
um fato operacional.

### Pagamento recebe o dono pelo evento

O pagamento precisa saber de quem é, mas o `PaymentService` não conhece o
`OrderService` — e não deve: o consumidor do RabbitMQ passaria a depender do
pedido para processar uma cobrança.

A solução usa o que já existia: o `userId` viaja no `OrderConfirmedEvent`, que
é o ponto de integração entre os dois agregados. O pagamento guarda o dono como
cópia, pela mesma razão que o item do pedido guarda o preço — é um fato do
momento, não uma consulta viva.

### Dois tokens, porque são dois problemas opostos

Token assinado não se cancela, então validade longa significa janela longa para
quem o roubar. Validade curta resolve isso e cria outro: o usuário é expulso a
cada quinze minutos.

Os dois se empurram enquanto houver um token só. Com dois, cada um resolve o seu:

| | Validade | Guardado? | Serve para |
|---|---|---|---|
| **Access** | 15 min | não | toda requisição |
| **Refresh** | 7 dias | sim, no Redis | obter um access novo |

O refresh é guardado, então **pode ser apagado** — e apagá-lo revoga de verdade.
O access continua sem estado e sem consulta, porque a janela dele é de minutos.
Revogar deixa de ser impossível e passa a ter até quinze minutos de atraso.

A alternativa seria consultar uma lista negra a cada requisição. Funciona e é
imediata, mas devolve o estado ao caminho de autenticação: com o Redis fora,
sobra escolher entre barrar todo mundo ou aceitar token revogado.

O refresh **não é um JWT**: é uma string aleatória opaca. Como ele já precisa ser
consultado, assinar não acrescentaria nada — e opaco não entrega informação a
quem o roubar.

E o que fica no Redis é o **hash** dele, não o valor, pela mesma razão da senha:
um dump não deve virar sessão. Com uma diferença que importa — aqui é SHA-256,
não BCrypt. A lentidão do BCrypt protege segredo de baixa entropia; 256 bits
aleatórios são inquebráveis por força bruta de qualquer jeito, e pagar o custo
não compraria nada.

### Refresh reutilizado derruba o login inteiro

Cada uso do refresh emite um novo e invalida o anterior. Sem rotação, um token
de sete dias seria uma senha permanente trafegando.

A rotação habilita a parte interessante: se um refresh **já usado** reaparecer,
existem duas cópias dele — e isso é sinal de roubo. A resposta é derrubar todos
os tokens daquele login, não só o repetido:

```
atacante rouba o refresh
vítima usa primeiro    → rotaciona, recebe um novo
atacante usa o antigo  → 401, e a família inteira cai
vítima tenta o dela    → 401 também
```

Os dois perdem acesso, de propósito. Quem tem a senha faz login de novo; o
atacante não tem. Punir só o repetido deixaria o ladrão dentro se ele chegasse
primeiro.

Cada login abre a própria família, então sair num dispositivo não desconecta os
outros.

Um efeito colateral bem-vindo: o access token novo é montado lendo o usuário,
não copiando o que estava guardado. Rebaixar um administrador passa a valer em
até quinze minutos, em vez de esperar o refresh expirar.

### 401 e 403 são respostas diferentes

| | Significa |
|---|---|
| **401** | sem token, ou token inválido, adulterado ou expirado |
| **403** | token válido, mas a role não permite esta operação |

Manter a distinção importa: 401 diz "identifique-se", 403 diz "já sei quem você
é, e não pode". Responder 401 para os dois faria o cliente tentar logar de novo
sem necessidade.

Esses dois status são os únicos que **não** passam pelo `GlobalExceptionHandler`.
O Spring Security rejeita no filtro, antes de qualquer controller — sem
controller, não há `@RestControllerAdvice`, e o corpo sairia vazio. Dois
handlers registrados na cadeia devolvem o mesmo `ErrorResponse` das outras
falhas, para a API não ter duas gramáticas de erro.

### Falha de login não revela quem tem conta

Email inexistente e senha errada produzem resposta idêntica. Diferenciar as
duas transformaria o login num verificador de cadastro: com uma lista de emails
vazada de outro site, dá para descobrir quais são clientes daqui.

A mensagem igual não basta, porque **o tempo também é observável**. Conferir uma
senha custa centenas de milissegundos de propósito; email inexistente retornaria
quase instantâneo, e a diferença seria medível. Por isso a verificação de hash
roda também quando o email não existe, contra um hash descartável, para os dois
caminhos custarem o mesmo.

Esse hash é gerado pelo próprio `PasswordEncoder` na subida da aplicação, e não
fixado no código. O custo fica gravado dentro do hash: um valor fixo gerado com
custo baixo continuaria rápido em produção, e o vazamento seguiria aberto —
agora disfarçado.

### Listagens paginadas, com envelope próprio

As listagens devolvem uma página, não a tabela inteira:

```json
{
  "content": [ ... ],
  "page": 0,
  "size": 20,
  "totalElements": 137,
  "totalPages": 7
}
```

O `Page` do Spring Data poderia ir direto para o JSON, mas ele traz uma dúzia
de campos internos (`pageable`, `offset`, `numberOfElements`, `empty`...) e o
formato é do framework. Uma atualização de versão mudaria o contrato de quem
consome a API — então o `PageResponse` expõe só os cinco campos que importam.

É a mesma regra do `ProductResponse`: o que sai é decisão sua, não reflexo da
biblioteca. O `PageResponse` *importa* o `Page` para converter, e isso não é
contradição — o que não pode vazar é o JSON, não a lista de imports.

O tamanho padrão fica na anotação do endpoint, e não no `application.yml`,
porque `@PageableDefault` sempre fornece um valor próprio: a propriedade
`default-page-size` ficaria no arquivo parecendo configurar algo, sem nunca ser
consultada. O `max-page-size` continua no yml, esse o Spring aplica — sem ele,
`?size=1000000` seria aceito e a paginação não protegeria nada.

A ordenação padrão é explícita (`createdAt` decrescente). Sem ordem definida o
banco não garante consistência entre consultas, e o mesmo registro pode aparecer
na página 1 e sumir da 2.

### Coleção carregada em lote, não uma query por item

Listar pedidos montava uma query para os pedidos e **mais uma para os itens de
cada pedido** — o N+1 clássico. Com 20 pedidos por página, 22 consultas para
desenhar uma tela.

O reflexo seria `JOIN FETCH`, e é uma armadilha: combinado com paginação, o
Hibernate avisa `firstResult/maxResults specified with collection fetch` e
pagina **em memória**, carregando a tabela inteira. O join multiplica linhas, e
o `LIMIT` cortaria um pedido pela metade.

A saída é `@BatchSize(size = 20)` na coleção: em vez de uma query por pedido, os
itens vêm num `where order_id in (...)` só. As 22 consultas viram 3 — lista,
contagem e itens.

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

### Projeção de leitura em MongoDB

As consultas administrativas de vendas, por dia e por produto, são agregações
sobre `orders` e `order_items`. Rodadas no mesmo banco que recebe as escritas,
competem com elas. A projeção separa essa leitura: um documento por pedido, em
MongoDB, alimentado pelos eventos do RabbitMQ.

O MySQL continua sendo a única fonte de verdade. A projeção não recebe regra de
negócio e pode ser apagada e reconstruída a partir do MySQL, com
`--orderflow.views.rebuild=true`.

Eventos repetidos ou fora de ordem são tratados pela versão do pedido: a mesma
confirmação não duplica o documento, e um evento antigo não faz o status
regredir. Um status que chega antes da confirmação espera numa fila com TTL de
10 segundos, por até nove vezes, antes de ir para a DLQ. O retry na própria fila
travaria a confirmação atrás dele.

O preço do desenho é consistência eventual: uma venda confirmada pode levar
alguns segundos para aparecer no relatório. A alternativa de guardar a projeção
numa tabela do próprio MySQL foi considerada e fica registrada no
[ADR 0001](docs/adr/0001-read-model-pedidos-mongodb.md), junto com a
[especificação](specs/001-read-model-pedidos/spec.md).

A imagem é `mongo:7`, não a 8. O MongoDB 8 embute uma versão do TCMalloc
incompatível com o kernel Linux de 6.19 a 7.0.13 e, nesse intervalo, se recusa
a iniciar ([SERVER-121912](https://jira.mongodb.org/browse/SERVER-121912)).
Parte dos runners do GitHub já usa esse kernel, então o CI falhava de forma
intermitente. Na máquina local nunca falhou, porque o Docker Desktop roda no
kernel do WSL2, que é anterior. A projeção não usa nada que exija a 8, e a volta
para ela fica para quando o kernel dos runners passar de 7.0.13 ou sair uma
versão corrigida.

O container de teste espera o log `Waiting for connections` em vez da porta,
que é a espera padrão do Testcontainers. Com a espera pela porta, um `mongod`
que encerrava logo depois de abrir a porta só aparecia mais tarde, como
`MongoDBContainer should be started first`, sem pista da causa. Com a espera
pelo log, a falha sai no próprio start do container, com o código de saída do
`mongod`.

A conexão é configurada por `spring.mongodb.uri`. No Boot 4, a antiga
`spring.data.mongodb.uri` deixou de ser lida, sem erro na subida. Os testes não
percebiam, porque o Testcontainers injeta a conexão; no Compose, a aplicação
tentava o Mongo em `localhost`, e a projeção nunca gravava. O healthcheck do
container, acrescentado na separação em serviços, foi o que mostrou.

### Separação em microsserviços, uma capacidade por vez

O monólito está sendo separado em serviços, um por capacidade: identidade,
catálogo, carrinho, pedidos, pagamentos, relatórios de vendas e notificações. A
motivação é aprender e demonstrar arquitetura distribuída, não escala: as
fronteiras já existiam no código, e agregados já se referenciavam por `UUID`. A
alternativa mais forte, um monólito modular, custa muito menos e fica registrada
como a decisão a revisitar se manter vários serviços travar a evolução. Decisões
e alternativas estão em [research.md](specs/003-microsservicos/research.md).

A migração é incremental: a cada etapa, um pacote sai do monólito e o sistema
continua funcionando por inteiro. O cliente não percebe a mudança, porque entra
sempre pelo mesmo endereço.

### Módulos, e o que pode ser compartilhado

O repositório é Maven multi-módulo. As bibliotecas compartilhadas ficam em
`platform/`, divididas para que cada serviço carregue só o que usa:
`platform-web` (formato de erro e paginação), `platform-security` (validação do
token e regras de acesso), `platform-messaging` (exchange, DLQ e conversor dos
fatos), `platform-outbox` e `platform-test`. O gateway, por exemplo, não carrega o
resource server.

O `platform` não conhece domínio. As exceções que ele trata são genéricas por
status, como `ConflictException` (409) e `UnauthorizedException` (401), e cada
funcionalidade estende a sua: `UserAlreadyExistsException` mora no pacote de
usuários, não numa pasta compartilhada. Os fatos ficam no módulo `contracts`, que
o `platform` também não conhece; cada serviço entrega à mensageria o mapa dos
fatos que usa.

### Uma entrada para todos os serviços

O cliente usa só a porta 8080, servida por um Spring Cloud Gateway no mesmo
modelo servlet do resto do projeto. Ele roteia apenas os prefixos da API, sem
rota padrão: um caminho fora da tabela responde 404 no formato de erro de
sempre. Assim, as rotas internas (`/internal/**`, `/.well-known/jwks.json` e o
actuator dos serviços) ficam fora do alcance do cliente sem regra própria.

Quando o serviço de destino não responde, a entrada devolve `ErrorResponse`:
503 se a conexão falha e 504 se a resposta passa de 4,5 segundos. O valor fica
abaixo dos 5 segundos que a especificação admite para uma falha no checkout.
Erros produzidos pelos serviços passam sem alteração.

O gateway preserva o cabeçalho `Host`. O serviço monta o `Location` das respostas
201 e o endereço do "Try it out" da documentação como montava antes da entrada
única. A alternativa, confiar em `X-Forwarded-*`, exigiria aceitar esses
cabeçalhos de qualquer cliente, que poderia forjar o host. Para preservar o
`Host`, o gateway usa o Apache HttpClient 5: o cliente HTTP do JDK proíbe definir
esse cabeçalho e ainda tenta subir para HTTP/2, o que cancelava chamadas com
corpo.

A documentação também tem uma página só, servida pela entrada, com uma entrada
por serviço. Cada serviço documenta as próprias rotas, e as que a entrada não
repassa ficam fora do documento.

### Fatos com tipo versionado

Os eventos publicados entre serviços levam no cabeçalho `__TypeId__` um id
lógico, como `order.confirmed.v1`, e não o nome da classe Java. Com o nome da
classe, mover o evento de pacote quebraria todos os consumidores. O formato só
cresce: o consumidor ignora campos que não conhece, e remover ou renomear um
campo exige uma versão nova do fato. Um teste fixa os campos e os ids de cada
fato, para que uma quebra de contrato apareça no build.

O mapa entre id e classe é o `ClassMapper` do conversor do Spring AMQP. O
caminho previsto era o `DefaultJacksonJavaTypeMapper`, mas ele inverte o mapa num
`HashMap`: com o id lógico e o nome de classe antigo, aceito durante a transição,
apontando para a mesma classe, qual dos dois sairia na publicação dependeria do
hash. O `ClassMapper` publica sempre o id lógico e recusa qualquer id que não
conheça, em vez de instanciar a classe que a mensagem pedir.

### O rastro atravessa o outbox

Cada requisição ganha um identificador de rastro, propagado no cabeçalho
`traceparent` pelas chamadas HTTP e pelas mensagens, e enviado ao Zipkin com o
tempo de cada passo. O id também entra em toda linha de log.

O outbox é o ponto em que o rastro se perderia: o evento é gravado na transação
da requisição e publicado depois, por outra thread. Por isso a tabela guarda o
`traceparent` de quem gravou, e o relay publica dentro desse rastro. A cobrança
e a projeção de vendas aparecem no mesmo rastro da confirmação que as causou.

Um detalhe custou tempo e vale registrar: no Boot 4.1, a propagação do Brave só
existe com o envio dos rastros ligado. Os testes desligam o envio por padrão e,
com ele, a propagação; o teste que prova o rastro liga o envio com
`@AutoConfigureTracing` e desliga só o exportador do Zipkin.

## Endpoints

**Contas**

| Método | Rota | Status | Descrição |
|---|---|---|---|
| `POST` | `/auth/register` | 201 + `Location` | Cria uma conta |
| `POST` | `/auth/login` | 200 / 401 | Autentica e devolve o par de tokens |
| `POST` | `/auth/refresh` | 200 / 401 | Troca o refresh por um par novo |
| `POST` | `/auth/logout` | 204 | Revoga os tokens daquele login |
| `GET` | `/users/{id}` | 200 / 404 | Busca por id |
| `PUT` | `/users/{id}/name` | 200 / 400 / 404 | Altera o nome |
| `PUT` | `/users/{id}/email` | 200 / 400 / 404 / 409 | Altera o email |
| `PUT` | `/users/{id}/password` | 200 / 400 / 404 | Altera a senha |

O corpo do cadastro leva apenas nome, email e senha — a role é do sistema. A
senha não aparece em nenhuma resposta.

**Quem acessa o quê**

| Rota | Exige |
|---|---|
| `/auth/**`, Swagger, health | nada |
| `GET /products`, `GET /products/{id}` | nada — é vitrine |
| `POST`/`PUT`/`DELETE` `/products` | papel `ADMIN` |
| `POST /orders/{id}/ship` e `/deliver` | papel `ADMIN` |
| `/users/{id}` | ser o dono da conta, ou `ADMIN` |
| todo o resto | autenticação |

A última linha é literal: a regra final é *"qualquer outra requisição exige
autenticação"*. Assim uma rota nova nasce protegida, e esquecer de declará-la
falha fechando, não abrindo.

**Produtos**

| Método | Rota | Status | Descrição |
|---|---|---|---|
| `GET` | `/products` | 200 | Lista paginada de produtos |
| `GET` | `/products/{id}` | 200 / 404 | Busca por id |
| `POST` | `/products` | 201 + `Location` | Cria um produto |
| `PUT` | `/products/{id}` | 200 / 404 | Atualiza um produto |
| `DELETE` | `/products/{id}` | 204 / 404 | Remove um produto |

**Carrinho**

| Método | Rota | Status | Descrição |
|---|---|---|---|
| `GET` | `/cart` | 200 / 404 | Busca o carrinho com o preço atual |
| `POST` | `/cart/items` | 200 / 400 / 404 | Soma à quantidade; cria o carrinho no primeiro item |
| `PUT` | `/cart/items/{productId}` | 200 / 400 / 404 | Substitui a quantidade; zero remove |
| `DELETE` | `/cart/items/{productId}` | 200 / 404 | Remove o item |
| `POST` | `/cart/checkout` | 200 / 400 / 409 | Gera o pedido e descarta o carrinho |

Não há `cartId` na URL: `/cart` é sempre o carrinho de quem chama, identificado
pelo token. Também não há rota para criar um: ele nasce no primeiro item e morre
no checkout ou no fim do TTL. Um `POST /carts` vazio só criaria chave para ser
abandonada.

**Pedidos**

| Método | Rota | Status | Descrição |
|---|---|---|---|
| `GET` | `/orders` | 200 | Lista paginada de pedidos |
| `GET` | `/orders/{id}` | 200 / 404 | Busca por id |
| `POST` | `/orders` | 201 + `Location` | Cria um pedido |
| `POST` | `/orders/{id}/confirm` | 200 / 404 / 409 | Confirma o pedido |
| `POST` | `/orders/{id}/ship` | 200 / 403 / 404 / 409 | Marca como enviado (ADMIN) |
| `POST` | `/orders/{id}/deliver` | 200 / 403 / 404 / 409 | Marca como entregue (ADMIN) |
| `POST` | `/orders/{id}/cancel` | 200 / 404 / 409 | Cancela o pedido |

`GET /products` e `GET /orders` aceitam `?page=`, `?size=` e
`?sort=campo,asc|desc` — 20 por página, mais recentes primeiro, no máximo 100.

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

**Vendas (somente ADMIN)**

| Método | Rota | Status | Descrição |
|---|---|---|---|
| `GET` | `/admin/sales/daily?from=&to=` | 200 / 400 / 403 | Total por dia de confirmação, no fuso de São Paulo |
| `GET` | `/admin/sales/top-products?limit=` | 200 / 400 / 403 | Produtos mais vendidos, por quantidade |

Pedidos cancelados ficam fora dos totais. Os relatórios vêm da projeção, com
alguns segundos de atraso em relação ao pedido.

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
- [x] Listagens paginadas com envelope próprio e coleção carregada em lote
- [x] Cadastro de usuários com senha em hash BCrypt e role definida pelo sistema
- [x] Login com JWT, rotas protegidas por autenticação e papel
- [x] Recursos vinculados ao dono: carrinho, pedidos e pagamentos
- [x] Primeiro administrador criado na subida, por variável de ambiente
- [x] Outbox transacional: o evento commita junto com o pedido
- [x] Refresh token rotativo no Redis, com detecção de reuso e logout
- [x] Projeção de pedidos em MongoDB, com upsert guardado por versão
- [x] Fila de espera e DLQ para status que chega antes da confirmação
- [x] Reconstrução da projeção a partir do MySQL
- [x] Relatórios administrativos de vendas por dia e por produto
- [x] 422 testes, separados por velocidade (unitários, integração e ponta a ponta)
- [x] Separação em serviços, etapa 1: entrada única, credencial RS256 com JWKS,
  fatos com tipo versionado, rastreamento com Zipkin e linha de base de carga
- [x] Pipeline de CI rodando `mvn verify` a cada push
- [x] Imagem da aplicação e stack completa em Docker, com conexões por variável de ambiente
- [x] Documentação OpenAPI

**Próximos passos**

