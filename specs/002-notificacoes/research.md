# Research: Notificações de pedidos por e-mail

Decisões do [plano](plan.md). Cada uma traz o motivo e as alternativas
descartadas, como pede o princípio I da constituição. As referências "R da spec
003" apontam para [research.md](../003-microsservicos/research.md) daquela feature.

## R1. Serviço e armazenamento

- **Decisão**: `services/notifications`, com schema e usuário `notifications` no
  MySQL, Flyway e JPA, como o `payment`.
- **Motivo**: o estado da notificação é relacional e pede restrição de unicidade
  (um registro por pedido e status, FR-002) e consultas por estado e horário da
  próxima tentativa. O MySQL já está no Compose e no CI.
- **Alternativas consideradas**: MongoDB, como o `sales` (a unicidade e a busca
  por estado ficam mais simples em SQL, e o documento não traz vantagem aqui);
  Redis (sem a durabilidade que as tentativas pedem).

## R2. Destinatário em todos os fatos de pedido

- **Decisão**: `order.shipped.v1`, `order.delivered.v1` e `order.cancelled.v1`
  ganham o campo `userId`, que o `order.confirmed.v1` já tem. O id de tipo não
  muda: acrescentar campo é permitido pela regra 1 de
  [events.md](../003-microsservicos/contracts/events.md), e o `sales` ignora o
  campo novo. Uma mensagem publicada antes da mudança chega com `userId` nulo e é
  tratada como contato ausente (R6).
- **Motivo**: sem o `userId`, o serviço precisaria descobrir o dono do pedido. O
  fato de envio pode chegar antes da confirmação, e um pedido pendente cancelado
  nunca teve confirmação.
- **Alternativas consideradas**: guardar o dono a partir de `order.confirmed`
  (falha quando os fatos chegam fora de ordem e no cancelamento de pedido
  pendente); consultar o `orders` por HTTP a cada fato (acopla a disponibilidade
  dos dois serviços e exige credencial de serviço, que não existe).

## R3. Fatos de usuário pelo outbox do `identity`

- **Decisão**: `user.registered.v1` e `user.email-changed.v1`, com `userId`,
  `email` e `occurredAt`, no pacote `contracts.user`, com as chaves
  `user.registered` e `user.email-changed` na mesma exchange `orderflow.events`.
  O `identity` grava o fato pelo `platform-outbox`, na transação do cadastro e da
  troca de e-mail, e ganha a tabela `outbox_messages` e um usuário próprio no
  RabbitMQ com escrita na exchange. O administrador criado na subida não publica
  fato: ele existe antes de qualquer fila e chega pela carga inicial (R8).
- **Motivo**: a mesma garantia dos fatos de pedido (R10 da spec 003): o fato
  existe se e só se a mudança foi gravada. O e-mail viaja só nos fatos de
  usuário, nunca nos de pedido (FR-007).
- **Alternativas consideradas**: publicar direto depois do commit (perde o fato
  com o broker fora); o `notifications` consultar o `identity` a cada envio
  (acopla a disponibilidade e exige credencial de serviço).

## R4. Contato guardado por instante

- **Decisão**: `Contact` guarda `userId`, `email` e `updatedAt`, o instante da
  última mudança do usuário no `identity`. O fato leva esse instante em
  `occurredAt`, lido do usuário depois de gravado, e a exportação leva o mesmo
  campo. Um contato só é sobrescrito por um instante mais novo, venha do fato ou
  da carga.
- **Motivo**: o FR-009 proíbe a carga de sobrescrever um e-mail mais novo que
  chegou por fato, e dois fatos de troca podem chegar fora de ordem numa
  redelivery. O `updatedAt` já existe no `identity`, e fato e exportação o leem da
  mesma coluna.
- **Alternativas consideradas**: `@Version` no `User` (exige migração e liga
  trava otimista em todas as mudanças de usuário, uma mudança de comportamento
  fora do escopo); carga que só preenche contatos ausentes (resolve a carga, mas
  não a troca de e-mail fora de ordem).

## R5. Registrar e enviar em passos separados

- **Decisão**: o listener dos fatos de pedido só grava a notificação como
  `PENDING`, numa transação. A restrição única em `(order_id, order_status)` faz
  um fato repetido virar operação vazia (FR-002). Um envio periódico, a cada 5
  segundos e em lotes de 50, como o relay do outbox, busca as notificações
  pendentes cujo horário chegou, na ordem da versão do pedido, e tenta mandar.
- **Motivo**: com o e-mail fora do ar, o fato sai da fila e o estado fica
  guardado (FR-005, FR-006, FR-008). Primeira tentativa e retentativas seguem o
  mesmo caminho.
- **Alternativas consideradas**: mandar o e-mail no listener, com a retentativa do
  container (esgotada, a mensagem vai para a DLQ e o registro se perde, e a fila
  fica parada enquanto o e-mail falha); mandar depois do commit e usar o envio
  periódico só para as retentativas (dois caminhos para a mesma coisa).

## R6. Tentativas e estados finais

- **Decisão**: até 5 tentativas. Depois da tentativa `n` que falha, a próxima
  fica para 30 s × 2^(n−1) depois: 30 s, 1 min, 2 min e 4 min, cerca de 7,5
  minutos ao todo. Esgotadas, a notificação vira `FAILED`, com o último erro e um
  log de erro. Contato ausente conta como tentativa falha, porque ele pode chegar
  depois pela carga ou pelo fato; esgotadas as tentativas, vira `NO_RECIPIENT`.
  Prazos do SMTP: 2 s para conectar e 5 s para ler.
- **Motivo**: cobre uma queda curta do servidor de e-mail sem insistir sem parar
  (FR-005). Em operação normal, o primeiro envio sai em até 5 segundos, longe do
  minuto do SC-001.
- **Alternativas consideradas**: tentativas sem limite (esconde o problema, e o
  FR-005 pede limite); retentativa em memória com Resilience4j (perde a conta num
  reinício).

## R7. Status antigo não sai depois do novo

- **Decisão**: a notificação guarda a `version` do pedido que veio no fato. Antes
  de mandar, o envio confere se o mesmo pedido já tem notificação `SENT` com
  versão maior; se tiver, marca esta como `SUPERSEDED` e não manda. Como o envio
  segue a ordem da versão, status que chegam juntos saem na ordem certa.
- **Motivo**: o FR-003 fala de e-mail enviado, não de fato recebido, e a versão
  já vem em todos os fatos de pedido. Uma confirmação que chega depois do envio já
  notificado não sai, como o FR-003 manda.
- **Alternativas consideradas**: fila de espera com prazo, como no `sales` (mais
  peças para um caso que a versão resolve); mandar só o status mais novo de cada
  pedido (perderia o e-mail de envio quando a entrega chega logo depois, e a US2
  pede os dois).

## R8. Carga inicial dos contatos

- **Decisão**: o `identity` expõe `GET /internal/users/export`, paginado e
  restrito a `ADMIN`, com `userId`, `email` e `updatedAt`, fora do gateway e da
  documentação. O `notifications` expõe `POST /admin/notifications/contacts/import`,
  restrito a `ADMIN`, que repassa a credencial do administrador, grava com a guarda
  do R4 e responde quantos contatos gravou. Roda uma vez na entrada em operação; repetir não
  estraga nada.
- **Motivo**: é o molde da reconstrução do `sales` (R12 da spec 003). Não existe
  credencial de serviço; a carga automática na subida precisaria de uma.
- **Alternativas consideradas**: carga automática na subida (exige um fluxo de
  credencial de cliente no `identity`, que não existe); ler a tabela de usuários
  direto (viola a posse dos dados, FR-003 da spec 003).

## R9. Falhas visíveis para a operação

- **Decisão**: `GET /admin/notifications?state=FAILED`, paginado e restrito a
  `ADMIN`, lista as notificações por estado, com pedido, status, tentativas e
  último erro. O fim das tentativas também gera um log de erro.
- **Motivo**: o FR-005 pede a falha visível, e o FR-008 pede o registro. A rota
  responde sem acesso ao banco nem aos logs.
- **Alternativas consideradas**: só o log (não dá para consultar o que ficou para
  trás); métrica (o projeto não tem painel).

## R10. Envio de e-mail e caixa de teste

- **Decisão**: `spring-boot-starter-mail`, com o serviço dependendo da interface
  `MailSender`. Texto puro, em português, montado no código a partir de um modelo
  fixo por status. Em desenvolvimento e no `e2e`, o Mailpit do Compose (SMTP na
  1025, painel e API na 8025); nos testes de integração, o mesmo Mailpit por
  `GenericContainer`.
- **Motivo**: a biblioteca resolve o SMTP. Quatro textos fixos não justificam um
  motor de template. O Mailpit tem API HTTP para conferir as mensagens nos testes
  e é o mesmo nas três camadas. O health check do serviço não inclui o servidor
  de e-mail, que o starter acrescenta por padrão: com ele, o e-mail fora deixaria o
  `notifications` fora do ar para o Compose e travaria a subida do gateway, que
  depende do health. A queda do e-mail aparece nas tentativas (R6 e R9).
  O nome com que o serviço se apresenta ao servidor (`mail.smtp.localhost`) e o
  remetente da sessão (`mail.from`) são fixos: sem eles, o JavaMail resolve o nome
  da máquina no EHLO e no `Message-ID` de cada e-mail. No Windows de
  desenvolvimento, cada resolução levava cerca de 5 segundos, e os testes de
  integração falhavam ao acaso esperando um e-mail que saía com 10 segundos de
  atraso.
- **Alternativas consideradas**: Thymeleaf (dependência para quatro frases);
  GreenMail em memória (uma segunda ferramenta além da do Compose); MailHog (sem
  manutenção).

## R11. Testes

- **Decisão**: unitários para as regras da `Notification` (tentativas, horário da
  próxima, estados finais), da `Contact` (só o mais novo vence) e do envio
  (`SUPERSEDED`, contato ausente); integração para a unicidade no MySQL, o
  consumo pelo RabbitMQ, o envio pelo Mailpit e a carga com o `identity` no
  WireMock; no `identity`, o fato gravado no outbox junto com o cadastro e com a
  troca de e-mail; no `contracts`, os campos dos fatos novos e o `userId` nos três
  fatos. No `e2e`: `NotificationsE2E` (os quatro e-mails pela porta 8080),
  `FailureIsolationE2E` (T108 da spec 003), as notificações no
  `FullPurchaseE2E` e no `OrderFactsDeliveryE2E`, e o isolamento dos usuários
  novos no `DataOwnershipE2E`.
- **Motivo**: cada camada prova só o que carrega (princípio III).
- **Alternativas consideradas**: provar a unicidade só no `e2e` (lento e sem
  controle da repetição do fato).

## R12. Permissão por chave na exchange dos fatos

- **Decisão**: o `identity` publica na mesma exchange `orderflow.events`, e a
  permissão por chave de roteamento do RabbitMQ (topic permissions), criada pelo
  `rabbitmq-users`, limita o `orders` a `order.*` e o `identity` a `user.*`.
- **Motivo**: a permissão comum do RabbitMQ vale para a exchange inteira. Com o
  `identity` escrevendo nela, ele poderia publicar um `order.confirmed`, e a
  cobrança aconteceria. A permissão por chave mantém a garantia de que só o
  `orders` publica fatos de pedido, já provada no teste de posse dos dados.
- **Alternativas consideradas**: exchange própria para os fatos de usuário
  (separa também, mas cria uma segunda exchange de fatos, com DLX e conversor, e
  desfaz a decisão de exchange única do R9 da spec 003).
