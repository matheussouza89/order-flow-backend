# Feature Specification: Separação em microsserviços

**Feature Branch**: `003-microsservicos`

**Created**: 2026-10-20

**Status**: Draft

**Input**: User description: "Separar o projeto em microsserviços. Praticamente todas as features podem ser um microsserviço, inclusive carrinho, produtos e pedidos separados."

## Clarifications

### Session 2026-10-20

- Q: Quando um serviço chama outro em nome do cliente, como o serviço chamado sabe quem está pedindo e se pode confiar na chamada? → A: (a) A chamada leva a credencial do próprio cliente, e o serviço chamado aplica as mesmas regras de acesso de uma requisição externa.
- Q: Onde os serviços precisam rodar ao fim desta feature? → A: (a) Só na máquina local e no CI, com todos os serviços subindo juntos por um comando.
- Q: Quando uma requisição atravessa vários serviços e falha, como quem opera descobre em qual serviço ela parou? → A: (a) Um identificador comum passa por todos os serviços, inclusive pelos fatos publicados, e aparece em todos os registros; um painel mostra o caminho completo com o tempo de cada passo.
- Q: Quando um serviço muda o formato de um fato que publica, o que acontece com os serviços que já consomem esse fato? → A: (a) O formato só muda acrescentando informação; remover ou renomear exige uma nova versão do fato, mantendo a antiga até todos os consumidores migrarem.
- Q: Onde quem usa a API encontra a documentação das rotas depois da separação? → A: (a) Uma única página, na entrada única, com a documentação de todos os serviços.

### Session 2026-10-24

- Q: A separação inclui medir o desempenho sob carga? → A: Sim. Um teste de carga reproduzível mede o sistema depois da User Story 1, como linha de base, e de novo no fim da migração, com metas de tempo de resposta e de falhas.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Entrada única e credenciais verificáveis por qualquer serviço (Priority: P1)

O cliente e o administrador continuam usando um único endereço, com as mesmas rotas e respostas de hoje. As credenciais emitidas no login passam a poder ser verificadas por qualquer serviço, sem que esse serviço consiga emitir credenciais.

**Why this priority**: é a base de todas as outras etapas. Sem a entrada única, cada serviço extraído mudaria o endereço que o cliente usa. Sem a verificação independente, cada serviço precisaria do segredo que permite emitir credenciais.

**Independent Test**: rodar a suíte de testes atual pela entrada única e verificar que todas as rotas respondem igual. Verificar que uma credencial produzida fora do serviço de identidade é recusada.

**Acceptance Scenarios**:

1. **Given** o sistema atual, **When** o cliente faz qualquer requisição pela entrada única, **Then** recebe o mesmo status e o mesmo corpo que recebe hoje.
2. **Given** um usuário logado antes da troca do mecanismo de credencial, **When** a troca entra em operação, **Then** ele continua usando o sistema sem entrar de novo.
3. **Given** um serviço que só verifica credenciais, **When** alguém produz uma credencial a partir dele, **Then** essa credencial é recusada pelos demais serviços.

---

### User Story 2 - Relatórios de vendas como serviço próprio (Priority: P2)

Os relatórios de vendas do administrador passam a funcionar como um serviço independente, com os mesmos resultados de hoje.

**Why this priority**: o serviço só consome fatos publicados sobre pedidos, então é a extração de menor risco. É a primeira a provar que um serviço separado acompanha os pedidos sem consultar os dados deles.

**Independent Test**: com o serviço separado, confirmar pedidos e comparar os relatórios com os de antes da separação. Derrubar o serviço, criar pedidos, subir de novo e verificar que os relatórios incluem esses pedidos.

**Acceptance Scenarios**:

1. **Given** pedidos confirmados, **When** o administrador consulta vendas por dia e produtos mais vendidos, **Then** os números são iguais aos calculados antes da separação.
2. **Given** o serviço de relatórios fora do ar, **When** pedidos são confirmados, **Then** os pedidos são confirmados normalmente, e os relatórios os incluem quando o serviço volta.
3. **Given** os relatórios apagados, **When** a reconstrução é executada, **Then** os relatórios voltam completos, sem ler os dados internos do serviço de pedidos.

---

### User Story 3 - Pagamentos como serviço próprio (Priority: P3)

A cobrança passa a funcionar como um serviço independente e continua acontecendo quando o pedido é confirmado.

**Why this priority**: também só reage a fatos de pedido, mas lida com dinheiro e com um provedor externo. Por isso vem depois de os relatórios provarem o caminho.

**Independent Test**: confirmar um pedido e verificar que a cobrança acontece uma única vez. Derrubar o serviço, confirmar um pedido, subir de novo e verificar que a cobrança acontece.

**Acceptance Scenarios**:

1. **Given** um pedido confirmado, **When** o serviço de pagamentos recebe o fato, **Then** a cobrança é feita uma única vez, mesmo que o fato chegue repetido.
2. **Given** o serviço de pagamentos fora do ar, **When** um pedido é confirmado, **Then** a confirmação é aceita, e a cobrança acontece quando o serviço volta.
3. **Given** um cliente, **When** consulta um pagamento de outro cliente, **Then** recebe "não encontrado", como hoje.

---

### User Story 4 - Catálogo como serviço próprio (Priority: P4)

A vitrine e a administração de produtos passam a funcionar como um serviço independente. É a primeira capacidade da qual outra depende na hora: a finalização da compra precisa do nome e do preço do produto.

**Why this priority**: introduz a primeira dependência imediata entre serviços e o tratamento da falha dela.

**Independent Test**: consultar e administrar produtos pelo serviço separado. Com o catálogo fora do ar, tentar finalizar uma compra e verificar o erro claro e o carrinho intacto, e verificar que a consulta de pedidos existentes continua funcionando.

**Acceptance Scenarios**:

1. **Given** produtos cadastrados, **When** o cliente navega pela vitrine, **Then** vê os mesmos produtos de hoje.
2. **Given** o catálogo fora do ar, **When** o cliente tenta finalizar a compra, **Then** recebe um erro claro em tempo limitado, e o carrinho continua como estava.
3. **Given** o catálogo fora do ar, **When** o cliente consulta os próprios pedidos, **Then** a consulta funciona normalmente.
4. **Given** um produto cujo preço mudou depois do pedido, **When** o cliente consulta o pedido, **Then** vê o preço da compra, não o atual.

---

### User Story 5 - Carrinho como serviço próprio (Priority: P5)

O carrinho passa a funcionar como um serviço independente. Finalizar a compra transforma o carrinho num pedido no serviço de pedidos.

**Why this priority**: a finalização da compra vira uma sequência de passos entre serviços, e uma falha no meio não pode duplicar nem perder o pedido.

**Independent Test**: montar um carrinho, finalizar e verificar o pedido. Simular a perda da resposta da criação do pedido, repetir a finalização e verificar que existe um único pedido.

**Acceptance Scenarios**:

1. **Given** um carrinho com itens, **When** o cliente finaliza a compra, **Then** um pedido é criado com os itens, e o carrinho é esvaziado.
2. **Given** a resposta da criação do pedido perdida, **When** o cliente repete a finalização, **Then** existe um único pedido.
3. **Given** o serviço de pedidos fora do ar, **When** o cliente finaliza a compra, **Then** recebe um erro claro, e o carrinho continua como estava.
4. **Given** o serviço de carrinho fora do ar, **When** o cliente consulta os próprios pedidos, **Then** a consulta funciona normalmente.

---

### User Story 6 - Identidade como serviço próprio (Priority: P6)

Cadastro, login, renovação e encerramento de sessão passam a funcionar como um serviço independente.

**Why this priority**: todas as outras capacidades dependem de credenciais, mas a User Story 1 já as deixou verificáveis sem o emissor. Resta mover o emissor.

**Independent Test**: cadastrar, entrar, renovar e sair pelo serviço separado. Com a identidade fora do ar, verificar que quem já tem credencial válida continua usando as outras capacidades.

**Acceptance Scenarios**:

1. **Given** um usuário cadastrado, **When** entra, renova a sessão e sai, **Then** tudo funciona como hoje, inclusive a detecção de reuso da renovação.
2. **Given** o serviço de identidade fora do ar, **When** um usuário com credencial válida usa catálogo, carrinho ou pedidos, **Then** o uso continua até a credencial expirar.
3. **Given** um email inexistente e uma senha errada, **When** alguém tenta entrar com cada um, **Then** as respostas são iguais, inclusive no tempo, como hoje.

---

### User Story 7 - Pedidos como serviço próprio (Priority: P7)

O que resta do sistema único é o serviço de pedidos. Ao fim desta etapa, o sistema único deixa de existir.

**Why this priority**: é o centro do domínio e o último a sair, quando nenhuma outra capacidade depende dele por dentro.

**Independent Test**: com todas as capacidades separadas, executar o ciclo completo (cadastro, vitrine, carrinho, finalização, confirmação, cobrança, envio, entrega, relatório e notificação) e verificar que nada depende do sistema único.

**Acceptance Scenarios**:

1. **Given** todas as capacidades separadas, **When** o cliente faz o ciclo completo de compra, **Then** o resultado é o mesmo de hoje.
2. **Given** um pedido, **When** ele muda de status, **Then** pagamentos, relatórios e notificações recebem o fato, mesmo que um deles estivesse fora do ar no momento.

---

### Edge Cases

- Uma dependência imediata do checkout (catálogo ou pedidos) demora ou falha. O cliente recebe um erro em tempo limitado, no formato de erro atual, e nada fica pela metade.
- A resposta da criação do pedido se perde depois que o pedido foi criado. A repetição devolve o mesmo pedido, sem criar outro.
- Um serviço está fora do ar quando um fato é publicado. O fato é processado quando ele volta, sem perda e sem efeito duplicado.
- O mesmo fato chega duas vezes. O efeito acontece uma vez só, como já acontece hoje na projeção de vendas e na cobrança.
- Durante a migração, parte das capacidades está no sistema único e parte em serviços. O sistema funciona por completo em cada etapa.
- Os dados existentes de contas, produtos, pedidos e pagamentos chegam ao serviço dono. Nada se perde nem se duplica na migração.
- Um usuário estava logado quando o mecanismo de credencial mudou. Ele não precisa entrar de novo.
- A credencial do cliente expira no meio da finalização da compra. A finalização falha com o mesmo erro de credencial expirada de hoje, e o carrinho continua como estava.
- Um fato chega com informação que o consumidor ainda não conhece, porque quem publica foi atualizado antes. O consumidor processa o que conhece e ignora o resto.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Clientes e administradores DEVEM acessar todas as capacidades por um único endereço, com as mesmas rotas, os mesmos formatos de requisição e as mesmas respostas de hoje, inclusive o formato de erro.
- **FR-002**: Cada capacidade (identidade, catálogo, carrinho, pedidos, pagamentos, relatórios de vendas e notificações) DEVE poder ser iniciada, parada e implantada sem implantar as outras.
- **FR-003**: Cada serviço DEVE ser o único dono dos próprios dados. Nenhum serviço DEVE ler ou alterar diretamente os dados de outro.
- **FR-004**: Só o serviço de identidade DEVE conseguir emitir credenciais de acesso. Os demais DEVEM conseguir verificá-las sem conseguir emiti-las.
- **FR-005**: As regras de acesso atuais DEVEM valer igual em todos os serviços: papel exigido por rota, acesso só ao que é do próprio usuário e "não encontrado" para recurso alheio.
- **FR-006**: A indisponibilidade de pagamentos, relatórios de vendas ou notificações NÃO DEVE impedir criar, confirmar, enviar, entregar ou cancelar pedidos.
- **FR-007**: Um fato publicado enquanto o serviço interessado está fora do ar DEVE ser processado quando ele voltar, sem perda e com efeito único.
- **FR-008**: Quando uma dependência imediata falhar durante a finalização da compra, o cliente DEVE receber um erro em tempo limitado, no formato atual, e o carrinho DEVE continuar como estava.
- **FR-009**: Repetir a finalização da compra depois de uma falha NÃO DEVE criar pedido duplicado.
- **FR-010**: Os relatórios de vendas DEVEM poder ser reconstruídos do zero sem ler os dados internos do serviço de pedidos.
- **FR-011**: A troca do mecanismo de credencial NÃO DEVE obrigar quem já está logado a entrar de novo.
- **FR-012**: Os dados existentes DEVEM ser preservados na migração, sem perda nem duplicação.
- **FR-013**: A migração DEVE acontecer uma capacidade por vez, e o sistema DEVE funcionar por completo ao fim de cada etapa.
- **FR-014**: Uma chamada imediata entre serviços feita em nome de um cliente DEVE levar a credencial desse cliente, e o serviço chamado DEVE aplicar as mesmas regras de acesso de uma requisição externa. Nenhum serviço DEVE aceitar, numa rota que exige credencial, uma chamada sem credencial válida por ela vir de dentro do sistema.
- **FR-015**: Todos os serviços, com a infraestrutura de que dependem, DEVEM subir juntos por um único comando na máquina local e no CI.
- **FR-016**: Cada requisição DEVE receber um identificador que acompanha todas as chamadas e todos os fatos publicados a partir dela, e esse identificador DEVE aparecer nos registros de todos os serviços que ela atravessar.
- **FR-017**: Quem opera DEVE conseguir ver, a partir desse identificador, o caminho completo da requisição pelos serviços, com o tempo gasto em cada passo e o passo em que ela falhou.
- **FR-018**: O formato de um fato publicado DEVE mudar só acrescentando informação. Remover ou renomear informação DEVE gerar uma nova versão do fato, e a versão antiga DEVE continuar sendo publicada até todos os consumidores migrarem.
- **FR-019**: Um consumidor DEVE processar um fato que traga informação que ele ainda não conhece, usando o que conhece e ignorando o resto.
- **FR-020**: A documentação de todas as rotas DEVE ficar numa única página, acessível pela entrada única, mesmo com cada serviço documentando as próprias rotas.
- **FR-021**: O sistema DEVE ter um teste de carga reproduzível por um comando, que exercita vitrine, carrinho, finalização da compra e consulta de pedidos pela entrada única. O resultado da linha de base, medida depois da User Story 1, e o do fim da migração DEVEM ficar registrados lado a lado.

### Key Entities

- **Serviço**: uma capacidade do sistema que roda, guarda dados e é implantada sozinha. Atributos: a capacidade, os dados de que é dono, os fatos que publica e os fatos que consome.
- **Fato publicado**: algo que aconteceu num serviço e interessa a outros, como "pedido confirmado" ou "e-mail alterado". Atributos: o que aconteceu, a quem se refere, quando aconteceu, a versão do registro a que se refere (que ordena fatos sobre o mesmo registro) e a versão do formato.
- **Credencial de acesso**: a prova de identidade emitida no login. Atributos: usuário, papel e validade. Só a identidade emite; todos os serviços verificam.
- **Rastro**: o caminho de uma requisição pelos serviços. Atributos: o identificador comum e os passos, cada um com o serviço, a operação, a duração, o passo anterior e o resultado.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% das rotas atuais respondem com o mesmo status e o mesmo formato pela entrada única, verificados por testes ponta a ponta que percorrem todas as rotas.
- **SC-002**: Com pagamentos, relatórios de vendas e notificações fora do ar ao mesmo tempo, 100% das operações de pedido continuam funcionando.
- **SC-003**: Com o catálogo ou o serviço de pedidos fora do ar, a finalização da compra falha com mensagem clara em até 5 segundos, e 100% dos carrinhos ficam intactos.
- **SC-004**: Em testes de repetição da finalização da compra após falha, nenhum pedido é duplicado.
- **SC-005**: Depois que um serviço fora do ar volta, 100% dos fatos publicados no intervalo são processados, cada um uma única vez.
- **SC-006**: Cada etapa termina com a suíte completa verde, sem perder nenhuma capacidade que existia antes dela.
- **SC-007**: Nenhum usuário logado antes da troca do mecanismo de credencial precisa entrar de novo por causa dela.
- **SC-008**: Ao fim da última etapa, o ciclo completo de compra funciona sem o sistema único.
- **SC-009**: 100% das chamadas feitas direto a um serviço, sem passar pela entrada única, a rotas que exigem credencial e sem credencial válida, são recusadas.
- **SC-010**: Em 100% das falhas provocadas nos testes de finalização da compra, o caminho da requisição mostra o serviço em que ela parou, sem consultar os registros de cada serviço separadamente.
- **SC-011**: Em testes com um consumidor da versão anterior, 100% dos fatos publicados com informação acrescentada continuam sendo processados.
- **SC-012**: 100% das rotas públicas aparecem na página única de documentação da entrada única.
- **SC-013**: Com 50 clientes simultâneos durante 5 minutos, 95% das finalizações de compra respondem em até 1 segundo, e menos de 1% das requisições falha.
- **SC-014**: Com a mesma carga, 95% das consultas à vitrine respondem em até 300 milissegundos.
- **SC-015**: No fim da migração, o tempo de resposta de 95% das finalizações de compra é no máximo o dobro do medido na linha de base.

## Assumptions

- A motivação é aprender e demonstrar arquitetura distribuída num projeto de portfólio, não uma necessidade de escala.
- A API pública atual é o contrato: nenhuma rota existente muda de caminho, formato ou status. A feature acrescenta `POST /admin/sales/rebuild`, o cabeçalho opcional `Idempotency-Key` em `POST /orders` e rotas internas que a entrada única não expõe, e troca a opção de inicialização que reconstruía os relatórios pela rota nova.
- O teste de carga roda na máquina de desenvolvimento, com todos os serviços no mesmo computador. Os números valem como comparação entre a linha de base e o fim da migração, não como capacidade de produção; o README registra a máquina usada.
- O serviço de notificações é especificado à parte, em `specs/002-notificacoes`, e é o primeiro a nascer fora do sistema único, logo depois da User Story 1.
- Carrinho e pedidos são serviços separados, mesmo com o custo de a finalização da compra atravessar dois serviços.
- Não há ambiente de produção com usuários reais. Uma etapa pode exigir parar o sistema por alguns minutos.
- Os serviços rodam na máquina local e no CI. Implantar em orquestrador de containers ou em nuvem fica fora desta feature e pode ser uma feature seguinte.
- Cada user story é planejada e entregue como uma etapa própria, na ordem das prioridades.
