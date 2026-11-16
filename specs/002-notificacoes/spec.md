# Feature Specification: Notificações de pedidos por e-mail

**Feature Branch**: `002-notificacoes`

**Created**: 2026-10-20

**Status**: Draft

**Input**: User description: "Serviço de notificações que envia e-mail ao cliente quando o pedido é confirmado, enviado, entregue ou cancelado. Primeiro microsserviço extraído do monólito, para que a falha do envio não afete o processamento de pedidos."

## Clarifications

### Session 2026-10-20

- Q: De onde o serviço de notificações obtém o e-mail do cliente? → A: (b) Cópia mantida a partir dos eventos de cadastro e de alteração de e-mail, sem o e-mail nos eventos de pedido.
- Q: Quais clientes entram na cópia de e-mails no primeiro deploy? → A: (a) Todos os clientes cadastrados, copiados uma vez a partir da tabela de usuários.

### Session 2026-11-15

- Q: Com a separação em serviços concluída (spec 003), quem publica os eventos de cadastro e de alteração de e-mail, e de onde vem a carga inicial? → A: Do serviço de identidade, dono das contas. Ele publica os dois eventos, e a carga inicial lê os clientes por uma exportação dele: a tabela de usuários é dado próprio da identidade, e nenhum outro serviço a lê direto.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - E-mail de confirmação (Priority: P1)

Quando o cliente confirma um pedido, ele recebe um e-mail com o número do pedido e o total. A mesma confirmação recebida mais de uma vez não gera um segundo e-mail.

**Why this priority**: é a notificação mais esperada pelo cliente e a que confirma que a compra foi aceita. Sozinha, já entrega valor.

**Independent Test**: confirmar um pedido de um cliente com e-mail cadastrado e verificar, na caixa de testes, que chegou uma mensagem com o número e o total. Entregar o mesmo evento duas vezes e verificar que só uma mensagem foi enviada.

**Acceptance Scenarios**:

1. **Given** um cliente com e-mail cadastrado e um pedido pendente, **When** o pedido é confirmado, **Then** o cliente recebe um e-mail com o número do pedido e o total.
2. **Given** o evento de confirmação já processado, **When** o mesmo evento chega de novo, **Then** nenhum e-mail adicional é enviado.

---

### User Story 2 - E-mails de envio e entrega (Priority: P2)

Quando o pedido é enviado ou entregue, o cliente recebe um e-mail informando o novo status.

**Why this priority**: acompanha a compra depois da confirmação, mas depende de a confirmação já funcionar.

**Independent Test**: avançar um pedido confirmado para enviado e depois para entregue, e verificar que cada mudança gerou uma mensagem com o status correspondente.

**Acceptance Scenarios**:

1. **Given** um pedido confirmado, **When** é marcado como enviado, **Then** o cliente recebe um e-mail informando o envio.
2. **Given** um pedido enviado, **When** é marcado como entregue, **Then** o cliente recebe um e-mail informando a entrega.

---

### User Story 3 - E-mail de cancelamento (Priority: P3)

Quando o pedido é cancelado, o cliente recebe um e-mail informando o cancelamento.

**Why this priority**: caso de menor frequência. A regra de cancelamento já existe e só precisa ser comunicada.

**Independent Test**: cancelar um pedido confirmado e verificar que o e-mail de cancelamento chegou.

**Acceptance Scenarios**:

1. **Given** um pedido confirmado que ainda não foi enviado, **When** é cancelado, **Then** o cliente recebe um e-mail informando o cancelamento.

---

### Edge Cases

- O cliente não tem e-mail cadastrado. O pedido segue normalmente, a notificação fica registrada como não enviada por falta de destinatário, e nada é bloqueado.
- O serviço de e-mail está indisponível. O pedido é processado normalmente; as notificações ficam pendentes e são tentadas de novo, com limite de tentativas.
- Um evento de status mais antigo chega depois de um mais recente, por exemplo "entregue" antes de "enviado". O cliente não recebe o e-mail do status antigo depois do novo.
- O envio acontece, mas o registro do resultado falha. O cliente pode receber o mesmo e-mail duas vezes. Esse caso é aceito e documentado nas premissas.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema DEVE enviar um e-mail ao cliente quando um pedido for confirmado, enviado, entregue ou cancelado.
- **FR-002**: O sistema NÃO DEVE enviar mais de um e-mail por pedido e por status em operação normal, mesmo que o evento correspondente seja recebido mais de uma vez.
- **FR-003**: O sistema NÃO DEVE enviar um e-mail de status anterior depois de já ter enviado o de um status posterior do mesmo pedido.
- **FR-004**: O e-mail DEVE informar o número do pedido e o novo status. O e-mail de confirmação DEVE informar também o total.
- **FR-005**: Quando o envio falhar, o sistema DEVE tentar de novo, com limite de tentativas. Esgotado o limite, a notificação DEVE permanecer registrada como não enviada, de forma visível para a operação.
- **FR-006**: A falha ou a indisponibilidade do serviço de e-mail NÃO DEVE impedir a confirmação, o envio, a entrega ou o cancelamento de pedidos.
- **FR-007**: O sistema DEVE obter o e-mail do cliente sem que esse dado esteja nos eventos de pedido. O serviço mantém uma cópia do e-mail vigente, alimentada por eventos de cadastro e de alteração de e-mail publicados pelo serviço de identidade.
- **FR-008**: O sistema DEVE registrar, para cada notificação, o status enviado, o resultado e o número de tentativas.
- **FR-009**: Na primeira entrada em operação, o sistema DEVE copiar uma única vez, a partir do serviço de identidade, o e-mail de todos os clientes já cadastrados. A carga NÃO DEVE sobrescrever um e-mail mais recente recebido por evento enquanto ela acontece.

### Key Entities

- **Notificação**: uma mensagem a ser enviada por causa de uma mudança de status de um pedido. Atributos: pedido, status, referência ao cliente, estado (pendente, enviada, não enviada por falta de destinatário, falhou após as tentativas) e número de tentativas.
- **Contato do cliente**: o identificador do cliente e o e-mail vigente. Não contém outros dados pessoais.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Em operação normal, 99% dos pedidos confirmados geram o e-mail de confirmação em até 1 minuto.
- **SC-002**: Em testes de entrega repetida de eventos, sem falha de envio, nenhum pedido recebe dois e-mails para o mesmo status.
- **SC-003**: Com o serviço de e-mail indisponível, 100% dos pedidos continuam sendo confirmados, enviados, entregues ou cancelados, sem erro para quem opera a loja.
- **SC-004**: Em testes com eventos fora de ordem, nenhum cliente recebe um e-mail de status anterior depois do posterior.
- **SC-005**: Ao fim da carga inicial, 100% dos clientes cadastrados antes do deploy têm e-mail conhecido pelo serviço.

## Assumptions

- Nesta versão, o canal é somente e-mail, em português.
- Os eventos de confirmação, envio, entrega e cancelamento já são publicados pelo serviço de pedidos.
- As notificações são uma capacidade própria, iniciada e parada sem as outras, como pedem o FR-002 e o FR-006 da spec 003.
- O conteúdo dos e-mails segue um modelo fixo. Não há personalização por cliente nesta versão.
- Em desenvolvimento, os e-mails são capturados por uma caixa de testes local, sem envio real.
- A entrega dos eventos é pelo menos uma vez. Um e-mail duplicado por falha entre o envio e o registro do resultado é aceito nesta versão e fica documentado.
- O serviço de identidade ainda não publica eventos de cadastro nem de alteração de e-mail. Essa publicação faz parte desta feature.
