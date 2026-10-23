# Data Model: Separação em microsserviços

A feature não cria domínio novo. Ela redistribui os dados existentes entre os
donos (FR-003) e acrescenta o mínimo que a separação exige. Decisões em
[research.md](research.md), R7 a R10.

## Dono de cada dado

| Serviço | Armazenamento | Dados | Origem hoje |
|---|---|---|---|
| `identity` | MySQL, schema `identity` | `users` | `V4` |
| `identity` | Redis, prefixo `refreshToken` | refresh tokens (hash SHA-256, família) | `RefreshToken` |
| `catalog` | MySQL, schema `catalog` | `products` | `V1` |
| `cart` | Redis, prefixo `cart` | carrinhos, com TTL | `Cart` |
| `orders` | MySQL, schema `orders` | `orders`, `order_items`, `outbox_messages` | `V2`, `V5`, `V6`, `V7` |
| `payment` | MySQL, schema `payment` | `payments` | `V3`, `V5` |
| `sales` | MongoDB | `order_views` | já separado |
| `notifications` | definido na spec 002 | notificações e contatos | novo |

Cada usuário de banco só tem permissão no próprio schema, e cada usuário do Redis
só alcança o próprio prefixo. Nenhum serviço lê dado de outro (FR-003).

## Mudanças nos dados

### `orders.orders`

| Campo | Tipo | Regra |
|---|---|---|
| `idempotency_key` | `VARCHAR(64)`, nulo | Chave do checkout (FR-009). Única junto com `user_id`. Pedidos anteriores ficam com nulo. |

Restrição: `UNIQUE (user_id, idempotency_key)`. O MySQL permite vários nulos numa
restrição única, então pedidos antigos não conflitam.

Quando a chave se repete para o mesmo usuário, o serviço devolve o pedido já
criado, sem criar outro.

### `outbox_messages` (em todo serviço que publica)

| Campo | Tipo | Regra |
|---|---|---|
| `traceparent` | `VARCHAR(55)`, nulo | Contexto do rastro no formato W3C, gravado junto com a mensagem e restaurado ao publicar (FR-016). Nulo para mensagens anteriores. |

O resto da tabela não muda: `id`, `routing_key`, `payload_type`, `payload`,
`created_at`, `published_at`, `attempts`, `last_error`.

### Carrinho (Redis)

| Campo | Tipo | Regra |
|---|---|---|
| `checkoutKey` | texto, nulo | Gerado na primeira tentativa de finalizar e reaproveitado nas repetições (FR-009). Some com o carrinho depois do sucesso. |

## Movimentação dos dados existentes

Na etapa de extração de cada serviço, um script operacional em
`docker/mysql/migrations/` executa `RENAME TABLE orderflow.<tabela> TO
<schema>.<tabela>`, uma vez e como root, nos volumes que já têm dados. A operação
é atômica e não copia linhas (FR-012). O Flyway do serviço novo começa com
`CREATE TABLE IF NOT EXISTS`, que cria as tabelas num banco vazio, como nos testes
e em ambientes novos, e não faz nada quando elas já chegaram pelo script.

| Etapa | Tabelas movidas | Schema |
|---|---|---|
| US3 | `payments` | `payment` |
| US4 | `products` | `catalog` |
| US6 | `users` | `identity` |
| US7 | `orders`, `order_items`, `outbox_messages` | `orders` |

As chaves do Redis não mudam de nome; só passam a ser acessadas pelo usuário de
ACL do serviço dono.

## Entidades da spec

### Fato publicado

Algo que aconteceu num serviço e interessa a outros. O catálogo completo está em
[contracts/events.md](contracts/events.md).

| Atributo | Onde fica |
|---|---|
| o que aconteceu | id lógico do tipo, com versão do formato (`order.confirmed.v1`) |
| a quem se refere | `orderId`, `userId` no corpo |
| quando aconteceu | `occurredAt` no corpo |
| versão do registro | `version` no corpo; ordena fatos sobre o mesmo pedido |
| versão do formato | sufixo do id do tipo e da chave de roteamento |
| rastro | cabeçalho `traceparent` |

Regras: o formato só cresce (FR-018); o consumidor ignora o que não conhece
(FR-019); o efeito é único mesmo com o fato repetido (FR-007).

### Credencial de acesso

Token assinado com RS256 pelo emissor; verificado por todos com a chave pública.

| Atributo | Claim |
|---|---|
| emissor | `iss` = `orderflow` |
| usuário | `sub` = id do usuário |
| papel | `roles` |
| validade | `iat`, `exp` (15 minutos, como hoje) |
| chave que assinou | cabeçalho `kid` |

O refresh token continua opaco, guardado como hash no Redis do `identity`, e não
muda com a troca do algoritmo (FR-011).

### Rastro

O caminho de uma requisição pelos serviços. Não é guardado pelos serviços: cada um
envia os passos ao Zipkin, que monta o rastro.

| Atributo | Onde fica |
|---|---|
| identificador comum | id do rastro, propagado em `traceparent` (HTTP e mensagens) |
| passo | span: serviço, operação, início, duração, passo anterior, resultado |

### Serviço

| Atributo | Onde fica |
|---|---|
| capacidade | módulo em `services/` |
| dados de que é dono | tabela "Dono de cada dado" acima |
| fatos que publica e consome | [contracts/events.md](contracts/events.md) |
| rotas | [contracts/gateway-routes.md](contracts/gateway-routes.md) |
