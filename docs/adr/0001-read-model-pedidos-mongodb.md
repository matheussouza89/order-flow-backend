# 0001. Read model de pedidos em MongoDB

- **Status:** Aceito
- **Data:** 2026-10-13
- **Spec:** [read-model-pedidos](../../specs/001-read-model-pedidos/spec.md)

## Contexto

Falta uma leitura de pedidos que o MySQL responde mal: vendas por dia e produtos
mais vendidos, para o administrador. São agregações sobre `orders` e
`order_items`, executadas no mesmo banco que recebe as escritas transacionais
(criar, confirmar, cobrar).

A motivação é a forma das consultas, não um problema de desempenho medido. O
volume atual não exige nada disso.

Um pedido já tem forma de documento: um cabeçalho com uma lista de itens que são
lidos juntos. Um documento com os itens embutidos é a mesma ideia do retrato do
item do pedido, aplicada à leitura.

## Decisão

Manter uma projeção de leitura em MongoDB, com um documento por pedido,
alimentada por eventos do RabbitMQ.

- O MySQL continua sendo a única fonte de verdade. A projeção não recebe regra
  de negócio e pode ser apagada e reconstruída.
- O consumidor é idempotente: o `_id` do documento é o id do pedido, e um evento
  só é aplicado se a versão dele for maior que a gravada.

## Alternativas

1. **Tabela de projeção no próprio MySQL**, atualizada na mesma transação do
   pedido. É mais simples: sem segundo banco, sem consistência eventual, sem
   infraestrutura nova. É a alternativa mais forte. A escolha de MongoDB só se
   sustenta se o modelo de documento e a separação de carga justificarem um
   segundo banco.
2. **Índices e réplica de leitura no MySQL.** Resolve a carga, mas não o formato:
   as agregações continuam sobre tabelas normalizadas.
3. **OpenSearch ou Elasticsearch.** Forte em busca textual, que não é o problema
   aqui, e com custo operacional alto.
4. **MongoDB como fonte primária.** Descartada. As invariantes do pedido (máquina
   de estados, total, checkout) ficam no domínio relacional, como o README já
   defende.

## Consequências

Ganha-se:

- agregações administrativas fora do MySQL transacional;
- um documento que corresponde à forma como o pedido é lido;
- uma projeção descartável, reconstruível a partir do MySQL.

Paga-se:

- consistência eventual: a leitura pode estar alguns milissegundos atrás do
  pedido, e a API precisa documentar isso;
- um banco a mais no `docker-compose`, no CI e nos testes de integração;
- o evento de confirmação passa a carregar os itens, e três eventos novos
  (envio, entrega e cancelamento) precisam existir;
- mensagens que nunca podem ser aplicadas exigem uma DLQ, que ainda não existe;
- pedidos anteriores à mudança do evento não trazem itens. A reconstrução a
  partir do MySQL preenche isso.

Esta decisão deve ser revista se o volume de leitura nunca justificar o segundo
banco. Nesse caso, a alternativa 1 passa a ser a escolha.
