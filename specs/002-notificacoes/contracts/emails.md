# Contrato: e-mails

Texto puro, em português, com modelo fixo (R10). Remetente por `MAIL_FROM`,
com padrão `nao-responda@orderflow.local`. O número do pedido é o `orderId`; o
total segue o formato brasileiro, `R$ 1.234,56`.

| Status | Assunto | Corpo |
|---|---|---|
| `CONFIRMED` | `Pedido {orderId} confirmado` | `Seu pedido {orderId} foi confirmado. Total: {total}.` |
| `SHIPPED` | `Pedido {orderId} enviado` | `Seu pedido {orderId} foi enviado.` |
| `DELIVERED` | `Pedido {orderId} entregue` | `Seu pedido {orderId} foi entregue.` |
| `CANCELLED` | `Pedido {orderId} cancelado` | `Seu pedido {orderId} foi cancelado.` |

O FR-004 pede o número e o status em todos, e o total na confirmação.
