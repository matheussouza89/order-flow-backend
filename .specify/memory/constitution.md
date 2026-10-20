# OrderFlow Constitution

## Core Principles

### I. Decisões têm motivo escrito
Toda decisão de desenho que vale registrar tem o porquê documentado: no README ou
nos artefatos da feature no Spec Kit. O código mostra o que foi feito; a
documentação mostra por que foi feito assim. Decisão difícil de desfazer, com
alternativas plausíveis, fica no `research.md` da feature, com a decisão, o motivo
e as alternativas descartadas. Os ADRs em `docs/adr/` anteriores a esta regra
ficam como histórico e não recebem novos registros.

### II. Menos código, desde que diga mais
Antes de escrever código, verificar se a biblioteca já resolve. Sem DTO intermediário
que só carrega valor, sem remontar objeto que já existe, sem `if` que o tipo já
garante. Nomes descrevem a operação (`confirm()`, `changeRole()`), não a mudança de
estado (`setStatus()`). Código não tem comentários: o nome e a estrutura explicam, e
o porquê vai para a documentação ou para a mensagem de commit.

### III. Toda mudança de comportamento tem teste
Cada camada prova só o que carrega: testes unitários cobrem regra e borda com tudo
mockado; testes de integração cobrem o que só um banco real prova. Quando a regra é
conhecida antes do código, o teste vem primeiro e descreve a regra. Os testes mais
valiosos afirmam o que não acontece: a senha não aparece no JSON, uma mudança
rejeitada não altera o objeto, com o circuito aberto nenhuma requisição chega ao
gateway.

### IV. O domínio decide; o cliente não envia o que o domínio deriva
Entidades validam as próprias invariantes no construtor e nos métodos de mudança.
Preço, total, status e role nunca aparecem em DTO de entrada. Ações têm nome de
negócio, e o domínio decide o resultado.

### V. Fronteira pela visibilidade
Organização por funcionalidade, não por camada. Entidade, repositório e controller
são package-private; a service é a porta de entrada. Algo sobe para `shared/` só
quando não conhece o domínio e mais de uma funcionalidade precisa dele.

### VI. Erros têm um formato só
Todo erro tratado sai como `ErrorResponse` (`status`, `message`, `errors`). 400 é
requisição inválida; 401 é falta de identificação; 403 é papel insuficiente; 404
vale para recurso que não existe ou que não é do chamador; 409 é requisição correta
com estado que impede a operação. Recurso alheio responde 404, para não confirmar a
existência de identificadores.

### VII. Segurança por construção
Senha só como hash BCrypt, validada antes de virar hash. A role nunca vem do cliente.
Regra de papel fica em `@PreAuthorize` na controller; regra de dono fica em código na
service, porque precisa valer também fora do HTTP. Refresh token é opaco e guardado
como hash. Dependência de segurança é verificada no classpath antes de confiar na API.

## Padrões de trabalho

- Código e API em inglês; conversa, README, documentação e commits em português.
- Commits seguem Conventional Commits em português: assunto no imperativo, até
  cerca de 50 caracteres; corpo explica o porquê, em linhas de até 72 colunas. Não
  há trailer de co-autoria nem atribuição a IA.
- Antes de encerrar uma mudança: `./mvnw verify` verde; README atualizado quando a
  mudança tem um porquê que vale registrar; descrição da API e anotações Swagger
  conferidas; contagem de testes do README em dia.
- Dependências têm a versão verificada no classpath antes de a API ser usada: classes
  mudam de pacote e de nome entre versões major.

## Modelo de persistência das specs

As specs usam o modelo **flow-back**: os artefatos (`spec.md`, `plan.md`, `tasks.md`)
são editados no lugar e reconciliados. Quando um requisito muda, a spec é atualizada
primeiro, depois o plano e as tarefas, e só então o código. Os artefatos de uma
feature concluída continuam editáveis; o histórico fica no git.

## Governance

Esta constituição prevalece sobre práticas informais. Mudanças nela exigem commit
próprio com justificativa, e a versão sobe conforme o impacto: MAJOR para remover ou
inverter um princípio, MINOR para acrescentar princípio ou seção, PATCH para ajuste
de redação.

**Version**: 1.1.0 | **Ratified**: 2026-10-20 | **Last Amended**: 2026-10-20
