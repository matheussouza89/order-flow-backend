# Plano técnico: read model de pedidos

- **Spec:** [read-model-pedidos](read-model-pedidos.md), v3
- **Decisão:** [ADR 0001](../adr/0001-read-model-pedidos-mongodb.md), aceito
- **Tarefas:** [read-model-pedidos-tarefas](read-model-pedidos-tarefas.md)

## Abordagem

Quatro frentes, nesta ordem, porque cada uma depende da anterior:

1. **Produtor:** o pedido ganha versão, o evento de confirmação ganha os itens, e
   envio, entrega e cancelamento passam a gerar evento.
2. **Infraestrutura:** DLQ com retry, e Mongo no compose e nos testes.
3. **Projeção:** consumidor com upsert guardado por versão, e reconstrução a
   partir do MySQL.
4. **Consulta:** dois endpoints administrativos sobre a coleção.

## Onde mora o código

Um pacote novo por funcionalidade, `sales/`, porque é o que muda junto:

```
sales/
├── SalesController       /admin/sales/*, package-private
├── SalesService          agregações, package-private
├── OrderViewProjector    consome os eventos, package-private
├── OrderView             documento Mongo, package-private
├── OrderViewRepository   package-private
└── DTOs de resposta      public
```

Os eventos continuam no pacote `order/`, como contrato público. A projeção os
consome sem depender da entidade `Order`.

## Decisões técnicas

- **Versão com `@Version` em `Order`.** A migration `V7` cria a coluna com
  `DEFAULT 0 NOT NULL`, para que linhas antigas não fiquem com valor indefinido.
- **`saveAndFlush` nas transições.** O Hibernate só incrementa a versão e o
  `@LastModifiedDate` no flush. Lidos antes dele, saem com o valor antigo no
  evento. Hoje `confirmOrder` usa `getUpdatedAt()` antes do flush, então o
  `occurredAt` pode estar defasado. Isso será confirmado com teste na T2.
- **Itens como record aninhado.** `OrderConfirmedEvent.Item` carrega
  `productId`, `productName`, `unitPrice`, `quantity` e `subtotal`. Não reutiliza
  `OrderItem`, que é package-private e não deve atravessar a fronteira do
  pacote.
- **Upsert com guarda de versão.** O filtro do update inclui `version < v`. Se o
  documento já tem versão igual ou maior, o upsert tenta inserir com o mesmo
  `_id` e falha com E11000. Esse erro é tratado como "já aplicado", não como
  falha.
- **Status sem documento.** O update de status não faz upsert. Se nenhum
  documento for afetado, lança exceção, que aciona o retry e, ao esgotar, a DLQ
  (C3).
- **Retry só na projeção.** A fila da projeção usa uma fábrica de listener
  própria, com 5 tentativas e backoff exponencial. A fábrica padrão, usada pelo
  pagamento, não muda. O consumidor de cobrança já tem sua política de
  resiliência com o gateway, e não deve ganhar uma segunda camada de repetição.
- **DLQ.** Um dead-letter exchange compartilhado fica no `RabbitMqConfig`. A
  fila da projeção aponta para ele, e cada consumidor continua declarando a
  própria fila, como o README já define.
- **Valores e fuso.** Valores monetários em `Decimal128`. A agregação diária usa
  `$dateToString` com `timezone: "America/Sao_Paulo"`. Dias sem venda são
  preenchidos em Java, não no pipeline.
- **Mensagens antigas sem `items`.** Mensagens publicadas antes da troca chegam
  com `items` e `version` nulos. `version` é `Long` porque o Jackson recusa `null`
  em primitivo, e isso faria o consumidor de pagamento falhar com mensagens que
  estão na fila durante o deploy. A projeção as confirma com log e não cria documento. A
  reconstrução (T8) cobre esses pedidos.
- **Conexão.** `MONGO_URI` com padrão local, como as demais conexões do projeto.

Os nomes exatos do starter de Mongo e das propriedades de retry dependem da
versão do Spring Boot 4. Eles serão confirmados no classpath antes de implementar,
como o CLAUDE.md pede.

## Riscos e conflitos encontrados na análise

1. **C3 e C8 disputavam o mesmo caso.** Resolvido com retry e DLQ ao fim do
   limite. A spec está na v3.
2. **Retry global mudaria o consumidor de pagamento.** Resolvido com fábrica
   própria para a projeção.
3. **Versão e `updatedAt` lidos antes do flush.** Resolvido com `saveAndFlush`.
   A ocorrência atual do `occurredAt` defasado será confirmada na T2.
4. **Contrato dentro do monólito.** Produtor e consumidor usam as mesmas
   classes, o que é simples aqui. Ao extrair um serviço, o contrato precisará sair
   do pacote `order` ou virar um schema versionado. Isso não faz parte deste plano,
   mas fica registrado.
5. **Conflito de versão.** Com `@Version`, duas transições simultâneas no mesmo
   pedido passam a gerar `OptimisticLockingFailureException`. Antes, a última
   escrita vencia sem aviso. A exceção é mapeada para 409 na T1.

## Fora deste plano

- Histórico do cliente, que continua no MySQL.
- Extração da projeção em serviço separado.
