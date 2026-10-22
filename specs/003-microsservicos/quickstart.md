# Quickstart: validar a separação em microsserviços

Cenários para provar os critérios de sucesso da [spec](spec.md) com o sistema
rodando. Os mesmos cenários são automatizados no módulo `e2e`. Rotas e formatos em
[contracts/](contracts/).

## Pré-requisitos

- JDK 21 e Docker com Compose.
- Portas livres: 8080 (entrada única) e 9411 (painel do rastro).

## 1. Build e testes

```bash
./mvnw verify
```

Esperado: todos os módulos verdes, com os testes unitários e de integração de cada
serviço e os testes de contrato dos fatos (SC-011).

```bash
./mvnw verify -Pe2e
```

Esperado: os testes `*E2E` sobem o Compose e passam (SC-001 a SC-005, SC-009,
SC-010 e SC-012).

## 2. Subir tudo por um comando (FR-015)

```bash
docker compose up --build -d
```

Esperado: infraestrutura, gateway e serviços saudáveis. Só o gateway publica a
porta 8080.

## 3. Entrada única e documentação (SC-001, SC-012)

Cadastre um usuário, faça login e liste produtos pela porta 8080, como hoje.
Esperado: mesmos status e corpos de antes da separação.

Abra `http://localhost:8080/swagger-ui.html`. Esperado: uma página só, com uma
entrada por serviço, cobrindo todas as rotas públicas.

## 4. Falha isolada (SC-002, SC-005)

```bash
docker compose stop payment sales notifications
```

Crie e confirme um pedido pela entrada única. Esperado: o pedido é confirmado
normalmente.

```bash
docker compose start payment sales notifications
```

Esperado: em segundos, a cobrança do pedido aparece em `GET /payments/orders/{id}`,
o pedido entra em `GET /admin/sales/daily`, e cada fato é processado uma vez só.

## 5. Dependência imediata fora do ar (SC-003)

```bash
docker compose stop catalog
```

Finalize a compra de um carrinho com itens. Esperado: 503 em `ErrorResponse` em
até 5 segundos, e `GET /cart` mostra o carrinho como estava. Consultar os próprios
pedidos continua funcionando.

```bash
docker compose start catalog
```

## 6. Checkout sem duplicar (SC-004)

Coberto pelo teste `e2e`, que perde a resposta da criação do pedido de propósito e
repete a finalização. Esperado: um único pedido para a chave do checkout.

## 7. Chamada direta sem credencial (SC-009)

```bash
docker run --rm --network orderflow_orderflow-network curlimages/curl -s -o /dev/null -w "%{http_code}\n" http://orders:8080/orders
```

Esperado: `401`. Nenhum serviço aceita chamada sem credencial por ela vir de
dentro da rede.

## 8. Rastro de uma requisição (SC-010)

Abra `http://localhost:9411` e busque o serviço `gateway`. Esperado: o rastro de
um checkout mostra `gateway` → `cart` → `orders` → `catalog`, com o tempo de cada
passo. Repetindo o cenário 5, o rastro marca a chamada ao `catalog` como erro.

Esperado também: os fatos do pedido aparecem no mesmo rastro, até `payment` e
`sales`, porque o outbox guarda o contexto.

## 9. Reconstrução dos relatórios (FR-010)

Com um token de administrador:

```bash
curl -s -X POST -H "Authorization: Bearer $ADMIN_TOKEN" http://localhost:8080/admin/sales/rebuild
```

Esperado: `200` com `{"rebuilt": n}`, e os relatórios iguais aos de antes.

## 10. Troca da credencial sem novo login (SC-007)

Faça login antes de implantar a User Story 1 e guarde o refresh token. Depois da
implantação, use o token de acesso antigo. Esperado: `401`. Chame
`POST /auth/refresh` com o refresh token. Esperado: um par novo, sem senha.

## Encerrar

```bash
docker compose down
```
