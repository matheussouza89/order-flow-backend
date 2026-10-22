# Contrato: fatos publicados

Fatos trafegam na exchange `orderflow.events` (topic). Cada consumidor declara a
própria fila. Decisões em [research.md](../research.md), R9 e R10.

## Cabeçalhos

| Cabeçalho | Conteúdo |
|---|---|
| `__TypeId__` | id lógico do tipo, com versão do formato (`order.confirmed.v1`) |
| `traceparent` | contexto do rastro, formato W3C (FR-016) |

Durante a transição, o consumidor aceita também os nomes de classe antigos no
`__TypeId__` (`com.matheus.orderFlow.order.OrderConfirmedEvent` e os demais), para
não perder mensagens que já estavam na fila.

## Fatos de pedido

Publicados pelo `orders` (pelo `monolith` até a US7), sempre pelo outbox.

### `order.confirmed.v1`

Chave de roteamento: `order.confirmed`. Consumidores: `payment`, `sales`,
`notifications`.

| Campo | Tipo | Observação |
|---|---|---|
| `orderId` | UUID | |
| `userId` | UUID | dono do pedido |
| `total` | decimal | |
| `items` | lista | `productId` (UUID), `productName`, `unitPrice`, `quantity`, `subtotal` |
| `version` | inteiro, nulo | versão do pedido; nulo em mensagens anteriores à projeção |
| `occurredAt` | instante (UTC) | |

### `order.shipped.v1`, `order.delivered.v1`, `order.cancelled.v1`

Chaves de roteamento: `order.shipped`, `order.delivered`, `order.cancelled`.
Consumidores: `sales`, `notifications`.

| Campo | Tipo |
|---|---|
| `orderId` | UUID |
| `version` | inteiro |
| `occurredAt` | instante (UTC) |

## Fatos de usuário

`user.registered.v1` e `user.email-changed.v1`, publicados pelo `identity`, são
definidos na spec 002 e seguem as mesmas regras.

## Regras de compatibilidade

1. O formato de um fato só muda acrescentando campos (FR-018).
2. Remover ou renomear um campo cria uma versão nova (`order.confirmed.v2`), com
   id de tipo e chave de roteamento próprios (`order.confirmed.v2`). A versão
   antiga continua sendo publicada até todos os consumidores migrarem.
3. O consumidor ignora campos que não conhece (FR-019). Garantido pelo conversor
   do Spring AMQP e verificado em teste (SC-011).
4. O efeito de um fato é único mesmo quando ele chega repetido (FR-007): cada
   consumidor usa a `version` do pedido ou uma chave de idempotência própria.
5. Uma mensagem que esgota as tentativas vai para a DLQ compartilhada
   (`orderflow.events.dlq`), com o cabeçalho `x-death` indicando a fila de origem.
