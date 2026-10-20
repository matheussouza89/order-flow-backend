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
- **Status sem documento.** O status que chega antes da projeção do pedido não é
  aplicado nem descartado. Ele é republicado numa fila de espera com TTL de 10 s e
  volta à projeção depois desse tempo, com um contador de adiamentos. Ao chegar a
  9 adiamentos, cerca de 90 s, vai para a DLQ. A fila principal não trava, então a
  confirmação que chega em seguida é aplicada antes da nova tentativa. A escolha
  está na spec, versão 4, no C3.
- **Retry só na projeção.** A fila da projeção usa uma fábrica de listener
  própria, com 5 tentativas e backoff exponencial. A fábrica padrão, usada pelo
  pagamento, não muda. A fábrica fica no `RabbitMqConfig` compartilhado, com nome
  próprio (`resilientListenerContainerFactory`), e só a projeção a usa. O consumidor de cobrança já tem sua política de
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
- **Reconstrução.** Lê o MySQL com JDBC, sem as entidades de `order`. Só entra o que
  a projeção gravaria: pedidos confirmados, e cancelados depois de confirmados. Os
  itens vêm de `order_items`, que existem mesmo antes dos eventos. O comando é
  `--orderflow.views.rebuild=true` na subida; a aplicação continua rodando depois.
- **Consultas de vendas.** Agregação com `$dateToString` no fuso `America/Sao_Paulo`,
  com pedidos cancelados fora. O período de `daily` é limitado a 366 dias, e o
  `limit` de `top-products` a 100. Parâmetro obrigatório ausente vira 400, não 500:
  o handler genérico não tratava `MissingServletRequestParameterException`.
- **Pacote confiável do conversor.** A projeção usa um listener único para os
  quatro tipos de evento. Nesse modo o conversor lê o tipo do cabeçalho
  `__TypeId__`, e o Jackson recusa tipos fora de `java.*` por padrão. O conversor
  compartilhado passa a confiar em `com.matheus.orderFlow.order`, o pacote do
  contrato de eventos. O pagamento não era afetado, porque o listener dele declara
  o tipo no parâmetro.

Os nomes exatos do starter de Mongo e das propriedades de retry dependem da
versão do Spring Boot 4. Na T5, confirmados no classpath: no Spring AMQP 4.1.1, `maxAttempts` virou
`maxRetries`, que não conta a primeira tentativa. O configurador do Boot 4 mudou
para `org.springframework.boot.amqp.autoconfigure`. Confirmado na T6: `spring-boot-starter-data-mongodb`
(com o `-test` correspondente) e `org.testcontainers:testcontainers-mongodb`, com a
classe `org.testcontainers.mongodb.MongoDBContainer`. A imagem de teste é
`mongo:7`, fixada na versão principal, enquanto os demais containers usam `latest`.
Começou como `mongo:8` e foi trocada porque o MongoDB 8 se recusa a iniciar em
kernel Linux de 6.19 a 7.0.13, que parte dos runners do CI já usa.

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
6. **Status sem documento (resolvido).** Com um único consumidor, o retry na
   própria fila travaria a confirmação atrás do status. Resolvido com fila de espera
   com TTL, que deixa a fila principal livre (ver as decisões técnicas e a spec,
   C3).

## Fora deste plano

- Histórico do cliente, que continua no MySQL.
- Extração da projeção em serviço separado.
