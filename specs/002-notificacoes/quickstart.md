# Quickstart: validar as notificações

Cenários para provar os critérios de sucesso da [spec](spec.md) com o sistema
rodando. Os mesmos cenários são automatizados nos testes `*E2E`, e os que pedem
controle fino da entrega dos fatos, nos `*IT` do `notifications`. Rotas, fatos e
textos em [contracts/](contracts/).

## Pré-requisitos

- JDK 21 e Docker com Compose.
- Portas livres: 8080 (entrada única) e 8025 (painel do Mailpit).
- Um volume do MySQL criado antes desta feature precisa do schema
  `notifications`: o passo de volume antigo do README aplica o script de schemas
  de novo.

## 1. Build e testes

```bash
./mvnw verify
```

Esperado: todos os módulos verdes, incluindo os testes de contrato dos fatos de
usuário e do `userId` nos fatos de pedido.

```bash
./mvnw verify -Pe2e -pl e2e -am
```

Esperado: os testes `*E2E` passam, entre eles `NotificationsE2E` e
`FailureIsolationE2E`.

## 2. Subir tudo

```bash
docker compose up --build -d
```

Esperado: `notifications` e `mailpit` de pé com o resto. O painel do Mailpit abre
em `http://localhost:8025`, vazio.

## 3. Confirmação (US1, SC-001)

Cadastre um cliente, ponha um item no carrinho, finalize e confirme o pedido pela
porta 8080. Esperado: em até 1 minuto, o Mailpit mostra uma mensagem para o
e-mail do cliente, com o número do pedido e o total, no texto de
[emails.md](contracts/emails.md).

## 4. Fato repetido (SC-002)

Coberto por teste de integração: o mesmo `order.confirmed.v1` entregue duas vezes
grava uma notificação e manda um e-mail. Pela API, confirmar de novo o mesmo
pedido responde 409 e nenhum e-mail novo chega.

## 5. Envio, entrega e cancelamento (US2, US3)

Com um administrador, marque um pedido confirmado como enviado e depois como
entregue. Esperado: duas mensagens novas, uma por status. Cancele outro pedido
confirmado. Esperado: uma mensagem de cancelamento.

## 6. E-mail fora do ar (SC-003, FR-005)

```bash
docker compose stop mailpit
```

Confirme, envie e entregue pedidos. Esperado: todas as operações respondem como
sempre. `GET /admin/notifications?state=PENDING` lista as notificações com
`attempts` subindo.

```bash
docker compose start mailpit
```

Esperado: as mensagens chegam na próxima tentativa, em até 4 minutos. Com o
Mailpit parado por mais de 7,5 minutos, elas aparecem em
`GET /admin/notifications?state=FAILED`.

## 7. Fora de ordem (SC-004)

Coberto por teste de integração: `order.delivered` entregue e enviado antes de
`order.shipped` do mesmo pedido; o envio fica `SUPERSEDED` e não sai.

## 8. Troca de e-mail

Troque o e-mail de um cliente por `PUT /users/{id}/email` e confirme um pedido
dele. Esperado: a mensagem vai para o e-mail novo.

## 9. Carga inicial (SC-005, FR-009)

Num volume com clientes cadastrados antes desta feature, com um administrador:

```bash
curl -X POST http://localhost:8080/admin/notifications/contacts/import -H "Authorization: Bearer $ADMIN_TOKEN"
```

Esperado: 200 com `imported` igual ao número de usuários. Uma segunda chamada
responde `imported` 0. Confirme um pedido de um cliente antigo: a mensagem chega.

## 10. Falha isolada (T108 e SC-002 da spec 003)

```bash
docker compose stop payment sales notifications
```

Crie, confirme, envie, entregue e cancele pedidos. Esperado: tudo responde como
sempre. Ao subir os três de novo, cada um recebe os fatos que perdeu, e os
e-mails chegam uma vez cada.
