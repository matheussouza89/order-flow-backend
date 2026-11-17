# Contrato: fatos

Valem as regras de compatibilidade e os cabeçalhos de
[events.md](../../003-microsservicos/contracts/events.md) da spec 003. Todos os
fatos trafegam na exchange `orderflow.events`. Decisões em
[research.md](../research.md), R2 e R3.

## Fatos de usuário

Publicados pelo `identity`, sempre pelo outbox. Pacote `contracts.user`, mapa
`UserFacts.TYPES`. O broker só aceita do `identity` as chaves `user.*`, e do
`orders`, as `order.*` (R12).

### `user.registered.v1`

Chave de roteamento: `user.registered`. Publicado no cadastro
(`POST /auth/register`). Consumidor: `notifications`.

| Campo | Tipo | Observação |
|---|---|---|
| `userId` | UUID | |
| `email` | texto | |
| `occurredAt` | instante (UTC) | `updated_at` do usuário depois de gravado (R4) |

### `user.email-changed.v1`

Chave de roteamento: `user.email-changed`. Publicado na troca de e-mail
(`PUT /users/{id}/email`). Consumidor: `notifications`.

Mesmos campos de `user.registered.v1`, com o e-mail novo.

O administrador criado na subida não publica fato (R3).

## Mudança nos fatos de pedido

`order.shipped.v1`, `order.delivered.v1` e `order.cancelled.v1` ganham `userId`
(UUID, dono do pedido), como o `order.confirmed.v1` já tem. O id de tipo continua
o mesmo (regra 1). O `notifications` trata `userId` nulo, de mensagem publicada
antes da mudança, como contato ausente (R6).

| Campo | Tipo |
|---|---|
| `orderId` | UUID |
| `userId` | UUID, nulo só em mensagem anterior à mudança |
| `version` | inteiro |
| `occurredAt` | instante (UTC) |

O contrato da spec 003 é atualizado junto com o código (modelo flow-back).

## Filas do `notifications`

| Fila | Chaves | Retentativa |
|---|---|---|
| `order.status.notifications` | `order.confirmed`, `order.shipped`, `order.delivered`, `order.cancelled` | a do container resiliente; esgotada, DLQ compartilhada |
| `user.contacts.notifications` | `user.registered`, `user.email-changed` | idem |

A retentativa da fila cobre só a gravação. A falha do e-mail não volta para a
fila: fica na notificação (R5).
