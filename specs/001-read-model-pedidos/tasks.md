# Tarefas: read model de pedidos

- **Plano:** [plan](plan.md)
- **Spec:** [spec](spec.md), v4

Ordem de execução. Cada tarefa termina com `./mvnw verify` verde e com o teste
que prova o critério citado.

| # | Tarefa | Critérios | Depende de |
|---|---|---|---|
| T1 | `@Version` em `Order`, migration `V7` com `DEFAULT 0 NOT NULL` e handler 409 para conflito de versão. **Concluída** | base de C2 | — |
| T2 | `saveAndFlush` nas transições. Teste de versão e `occurredAt` após cada transição. O `occurredAt` defasado foi confirmado por teste vermelho e corrigido. **Concluída** | C2 | T1 |
| T3 | `OrderConfirmedEvent` com `items` e `version`. Teste do payload gravado no outbox e leitura de mensagem antiga. **Concluída** | C5, C6 | T2 |
| T4 | Eventos `OrderShipped`, `OrderDelivered` e `OrderCancelled`, gravados no outbox nas transições. Teste de uma linha por transição. **Concluída** | C2, C3 | T2 |
| T5 | Dead-letter exchange compartilhado e fábrica de listener própria da projeção, com 5 tentativas e backoff exponencial. Teste de mensagem que sempre falha indo para a DLQ. **Concluída** | C8 | — |
| T6 | Mongo no `docker-compose`, `MONGO_URI` e container de teste em `TestcontainersConfiguration`. Teste de ping no Mongo real. **Concluída** | base | — |
| T7 | Documento `OrderView`, repositório, projetor com upsert guardado por versão e tratamento de E11000. Testes de integração com Mongo real, incluindo Awaitility para a consistência eventual. **Concluída**, com C7 só em nível unitário, porque o Mongo compartilhado não pode ser derrubado nos testes | C1, C2, C3, C4, C5, C6, C7, C9 | T3, T4, T5, T6 |
| T8 | Reconstrução a partir do MySQL, com a coleção apagada antes. Teste que compara com `order_items`. **Concluída** | C10 | T7 |
| T9 | `GET /admin/sales/daily` e `GET /admin/sales/top-products`, com `@PreAuthorize` na controller. Teste de 403 para usuário comum. **Concluída** | C11, C12, C13 | T7 |
| T10 | README (link para o ADR e para a spec), descrição no `OpenApiConfig` e contagem de testes. **Concluída** | — | T9 |

## Cobertura

Cada critério da spec aparece em pelo menos uma tarefa:

- C1 em T7; C2 em T2, T4 e T7; C3 em T4, T5 e T7; C4 em T7; C5 em T3 e T7;
- C6 em T3 e T7; C7 em T7; C8 em T5; C9 em T7; C10 em T8; C11, C12 e C13 em T9.

## Antes de implementar

Este plano passa pelo portão humano da análise. A implementação começa só depois
da aprovação do plano e das tarefas.
