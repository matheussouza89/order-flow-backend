# Spec: read model de pedidos

- **Status:** Clarificada (v4)
- **Decisão:** [ADR 0001](../../docs/adr/0001-read-model-pedidos-mongodb.md)

## Objetivo

Responder consultas administrativas sobre vendas, por dia e por produto, a partir
de uma projeção em MongoDB, sem consultar o MySQL transacional.

## Escopo desta fase

Dentro:

- a coleção `order_views`, com um documento por pedido;
- o consumo dos eventos de confirmação, envio, entrega e cancelamento;
- dois endpoints administrativos de agregação.

Fora:

- histórico do cliente: `GET /orders` continua no MySQL;
- pagamento: o status de cobrança não entra no documento;
- qualquer escrita de regra de negócio sobre a projeção.

## Estado atual

Verificado no código:

- `OrderConfirmedEvent` carrega `orderId`, `userId`, `total` e `occurredAt`.
  **Não carrega itens.**
- Envio, entrega e cancelamento **não publicam evento**.
- `Order` **não tem versão**.

Os três pontos exigem mudanças no produtor antes do consumidor.

## Mudanças no produtor

1. `OrderConfirmedEvent` passa a carregar `items` (`productId`, `productName`,
   `unitPrice`, `quantity`, `subtotal`) e `version`.
2. `Order` ganha um campo de versão, incrementado a cada alteração persistida.
3. `ship`, `deliver` e `cancel` gravam no outbox os eventos `OrderShipped`,
   `OrderDelivered` e `OrderCancelled`, com `orderId`, `version` e `occurredAt`,
   na mesma transação da mudança de status, pelo mesmo caminho de
   `OrderEventPublisher`.

Compatibilidade: `OrderConfirmedPaymentListener` consome o mesmo evento de
confirmação. Os campos novos devem ser ignorados por ele. Confirmar com teste.

## Documento

Coleção `order_views`. O `_id` é o id do pedido.

```json
{
  "_id": "3f2a8c11-...",
  "userId": "7b1e...",
  "status": "CONFIRMED",
  "total": 200.00,
  "items": [
    { "productId": "...", "productName": "Teclado", "unitPrice": 100.00, "quantity": 2, "subtotal": 200.00 }
  ],
  "version": 2,
  "confirmedAt": "2026-10-13T14:02:11Z",
  "updatedAt": "2026-10-13T14:05:40Z"
}
```

Valores monetários são `Decimal128`, não string, porque a soma da agregação de
vendas depende disso. Confirmar, na versão do Spring Data MongoDB em uso, como o
`BigDecimal` é convertido antes de implementar.

O documento não guarda nenhum dado pessoal além do `userId`.

## Comportamento

### Consistência e ordem

- **C1.** Um `OrderConfirmed` entregue duas vezes gera um único documento.
  *(integração)*
- **C2.** Um evento cuja `version` não é maior que a gravada é ignorado. Um
  `OrderShipped` (v3) que chega depois de `OrderDelivered` (v4) não altera o
  status. *(integração)*
- **C3.** Um evento de mudança de status cujo pedido ainda não tem documento não
  cria documento. Ele é adiado por uma fila de espera com TTL de 10 s e reentregue à
  projeção. A cada adiamento sem documento, o contador sobe. Após 9 adiamentos
  (cerca de 90 s), vai para a DLQ. A fila principal não trava.
  *(integração para a aplicação tardia; unitário para o esgotamento)*
- **C4.** Após o consumo, o documento reflete o estado do pedido no MySQL.
  Verificado com Awaitility, sem `sleep`. *(integração)*
- **C5.** O documento nunca mostra um item que o pedido não tinha. Os itens vêm
  só do evento de confirmação, que traz o snapshot do momento da compra.
  *(integração, comparando com `order_items`)*
- **C6.** `total` é a soma dos `subtotal` dos itens. *(unitário)*

### Falha

- **C7.** Se a gravação no Mongo falhar, a mensagem não é confirmada e volta para
  reprocessamento. Não há documento parcial, porque a escrita é um único
  documento. *(integração, com o Mongo parado)*
- **C8.** Mensagem que nunca pode ser aplicada não bloqueia a fila: depois de 5
  tentativas, com backoff exponencial, vai para a DLQ. *(depende de DLQ, que ainda não existe)*

### Privacidade

- **C9.** O documento não contém nome, e-mail nem qualquer outro dado pessoal do
  cliente. *(verificar o JSON gravado, como já se faz com o payload do token)*

### Reconstrução

- **C10.** A coleção pode ser apagada e reconstruída a partir do MySQL, com um
  comando, sem perda. A reconstrução também preenche os itens de pedidos
  anteriores à mudança do evento. *(integração)*

### Consultas

- **C11.** `GET /admin/sales/daily?from=&to=` devolve, por dia de confirmação no fuso
  `America/Sao_Paulo`, a soma de `total` dos pedidos não cancelados. Dias sem venda aparecem com zero,
  para que o gráfico não pule dias. *(integração)*
- **C12.** `GET /admin/sales/top-products?limit=` devolve os produtos ordenados
  pela quantidade vendida em pedidos não cancelados. `limit` tem máximo de 100,
  como nas demais listagens. *(integração)*
- **C13.** Os dois endpoints exigem papel `ADMIN`, com `@PreAuthorize` na
  controller, como no restante do projeto.

## Decisões

1. **Dia das vendas.** Os instantes são gravados em UTC, e o agrupamento de
   `daily` usa o dia local em `America/Sao_Paulo`. Um pedido confirmado às 22h
   em Brasília aparece no dia em que aconteceu, mesmo sendo o dia seguinte em UTC.
2. **Tentativas antes da DLQ.** 5, com backoff exponencial (C8).
3. **Fonte da reconstrução.** O MySQL (C10). Não há retenção de eventos.
4. **Status sem documento.** Vai para uma fila de espera com TTL de 10 s e volta
   à projeção, até 9 adiamentos (cerca de 90 s). Depois disso, vai para a DLQ.
   O retry na própria fila foi descartado: com um único consumidor, travaria a
   confirmação atrás do status.
5. **Reconstrução.** Lê o MySQL, sem as entidades. Entram só os pedidos que a
   projeção gravaria: confirmados, e cancelados depois de confirmados. A data de
   confirmação vem do evento no outbox; pedidos anteriores ao outbox usam a data
   de criação. Roda com `--orderflow.views.rebuild=true` na subida.
