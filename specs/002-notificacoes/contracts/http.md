# Contrato: rotas HTTP

Erros em `ErrorResponse`, como no resto da API. Decisões em
[research.md](../research.md), R8 e R9.

## `notifications`, pela entrada única

O gateway ganha a rota `/admin/notifications/**` e a documentação
`/v3/api-docs/notifications`.

### `GET /admin/notifications`

Restrita a `ADMIN`. Lista as notificações de um estado, da mais recente para a
mais antiga.

| Parâmetro | Padrão | Observação |
|---|---|---|
| `state` | `FAILED` | `PENDING`, `SENT`, `NO_RECIPIENT`, `FAILED` ou `SUPERSEDED` |
| `page`, `size` | 0, 20 | |

Resposta 200, `PageResponse` com:

| Campo | Tipo |
|---|---|
| `id` | UUID |
| `orderId` | UUID |
| `orderStatus` | texto |
| `state` | texto |
| `attempts` | inteiro |
| `lastError` | texto, nulo |
| `updatedAt` | instante |

O e-mail do cliente não aparece: a operação vê o pedido, não o contato.

| Status | Quando |
|---|---|
| 400 | `state` desconhecido |
| 401 | sem credencial |
| 403 | papel insuficiente |

### `POST /admin/notifications/contacts/import`

Restrita a `ADMIN`. Copia o e-mail de todos os usuários do `identity`, página a
página, repassando a credencial de quem chamou. Um contato só muda se o instante
recebido for mais novo (R4). Pode ser repetida.

Resposta 200: `{"imported": 12}`, quantos contatos foram gravados; os que já
estavam iguais ou mais novos não contam.

| Status | Quando |
|---|---|
| 401 | sem credencial |
| 403 | papel insuficiente |
| 503 | o `identity` não respondeu; o que já foi gravado continua valendo, e a carga pode ser repetida |

## `identity`, só na rede interna

### `GET /internal/users/export`

Restrita a `ADMIN`. Fora do gateway e da documentação, como
`/internal/orders/export`.

| Parâmetro | Padrão |
|---|---|
| `page`, `size` | 0, 100 |

Resposta 200, `PageResponse` com `userId`, `email` e `updatedAt`, na ordem do
cadastro. Nenhum outro dado do usuário sai.
