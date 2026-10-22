# Contrato: rotas HTTP novas e alteradas

As rotas de hoje continuam iguais para o cliente. Este arquivo lista só o que a
separação acrescenta. Decisões em [research.md](../research.md), R5, R6, R7 e R12.

## Regras para toda chamada entre serviços

- A chamada leva o cabeçalho `Authorization` da requisição que a originou (FR-014).
  O serviço chamado valida a credencial e aplica as mesmas regras de acesso de uma
  requisição externa.
- O cabeçalho `traceparent` é propagado pela biblioteca de rastreamento (FR-016).
- Falha ou demora do serviço chamado vira 503 em `ErrorResponse` para quem chamou,
  dentro do tempo da cadeia (SC-003).

| Chamada | Timeout | Circuit breaker |
|---|---|---|
| `orders` → `catalog` | 1,5 s | sim |
| `cart` → `catalog` | 1,5 s | sim |
| `cart` → `orders` | 3,5 s | sim |
| `sales` → `orders` (reconstrução) | 10 s por página | não |

## `GET /.well-known/jwks.json`

Serviço: `identity` (`monolith` até a US6). Rede interna. Sem credencial.

Resposta 200: conjunto de chaves públicas (JWK Set), com `kty` `RSA`, `kid`, `alg`
`RS256`, `use` `sig`, `n` e `e`. Nunca contém a chave privada.

## `POST /orders` — cabeçalho novo

Serviço: `orders`. Rota pública já existente; o corpo e as respostas não mudam.

| Cabeçalho | Obrigatório | Regra |
|---|---|---|
| `Idempotency-Key` | não | até 64 caracteres; único por usuário (FR-009) |

| Situação | Resposta |
|---|---|
| sem o cabeçalho | comportamento de hoje |
| chave nova | cria o pedido: 201 com `Location` |
| chave repetida, mesmo usuário | devolve o pedido já criado: 201 com o mesmo `Location` e o mesmo corpo |
| mesma chave, outro usuário | chaves independentes: cria o pedido |

## `GET /internal/orders/export`

Serviço: `orders`. Rede interna. Papel `ADMIN`.

Parâmetros: `page` (a partir de 0) e `size` (1 a 500, padrão 100).

Resposta 200: `PageResponse` com os pedidos que já foram confirmados:

| Campo | Tipo |
|---|---|
| `orderId` | UUID |
| `userId` | UUID |
| `status` | `CONFIRMED`, `SHIPPED`, `DELIVERED` ou `CANCELLED` |
| `total` | decimal |
| `items` | `productId`, `productName`, `unitPrice`, `quantity`, `subtotal` |
| `version` | inteiro |
| `confirmedAt` | instante (UTC) |

Erros: 401 sem credencial; 403 sem papel `ADMIN`; 400 para `size` fora do limite.

## `POST /admin/sales/rebuild`

Serviço: `sales`. Pública, pela entrada única. Papel `ADMIN`.

Reconstrói a projeção página a página a partir de `GET /internal/orders/export`,
repassando a credencial do administrador. Não apaga nada antes: grava com a mesma
guarda de versão dos fatos, então um documento só é substituído por dados mais
novos, e os relatórios continuam respondendo durante o processo. Funciona igual
com a projeção vazia.

Resposta 200: `{ "rebuilt": <quantidade de pedidos> }`.

Erros: 401; 403 sem papel `ADMIN`; 503 em `ErrorResponse` se o `orders` estiver
fora do ar. Uma reconstrução interrompida pode ser repetida do início.

Substitui a flag de inicialização `--orderflow.views.rebuild=true`, que deixa de
existir.
