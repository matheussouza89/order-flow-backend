# Data Model: Notificações de pedidos por e-mail

Decisões em [research.md](research.md).

## `notifications` (MySQL, schema `notifications`)

### Notification

Uma mensagem a mandar por causa de uma mudança de status de um pedido.

| Campo | Tipo | Regra |
|---|---|---|
| `id` | UUID | |
| `orderId` | UUID | obrigatório |
| `orderStatus` | `CONFIRMED`, `SHIPPED`, `DELIVERED`, `CANCELLED` | obrigatório; único junto com `orderId` (FR-002) |
| `orderVersion` | inteiro | versão do pedido no fato (R7) |
| `userId` | UUID, nulo | dono do pedido; nulo em fato anterior ao R2 |
| `total` | decimal, nulo | só na confirmação (FR-004) |
| `state` | `PENDING`, `SENT`, `NO_RECIPIENT`, `FAILED`, `SUPERSEDED` | começa em `PENDING` |
| `attempts` | inteiro | começa em 0; no máximo 5 (R6) |
| `nextAttemptAt` | instante | quando o envio pode tentar de novo |
| `lastError` | texto, nulo | até 300 caracteres, como no `payment` |
| `createdAt`, `updatedAt` | instante | auditoria |

Índices: único em `(order_id, order_status)`; `(state, next_attempt_at)` para o
envio periódico.

Transições, todas na própria entidade:

```text
PENDING ──sent()───────────────► SENT
PENDING ──failed(erro)─────────► PENDING   attempts+1, nextAttemptAt com o atraso do R6
        └─ na 5ª tentativa ────► FAILED
PENDING ──withoutRecipient()───► PENDING   attempts+1, mesmo atraso
        └─ na 5ª tentativa ────► NO_RECIPIENT
PENDING ──superseded()─────────► SUPERSEDED
```

Só `PENDING` muda; os outros estados são finais.

### Contact

O e-mail vigente de um cliente. Não guarda outro dado pessoal.

| Campo | Tipo | Regra |
|---|---|---|
| `userId` | UUID | chave |
| `email` | texto | obrigatório, até 150 caracteres, como no `identity` |
| `updatedAt` | instante | instante da mudança no `identity` (R4) |

Regra: `replaceIfNewer(email, updatedAt)` só muda o contato quando o instante
recebido é maior que o guardado. Vale para o fato e para a carga.

## `identity` (MySQL, schema `identity`)

### `outbox_messages`

A mesma tabela do `orders`, criada por uma migração nova do `identity`, com
`traceparent` (R10 da spec 003). Os fatos de usuário são gravados nela na
transação do cadastro e da troca de e-mail (R3).

Nenhuma coluna nova em `users`: o instante dos fatos e da exportação vem de
`updated_at`.

## Fatos

Definidos em [contracts/events.md](contracts/events.md).
