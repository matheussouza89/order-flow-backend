# Contrato: rotas da entrada única

O cliente usa um endereço só, na porta 8080 (FR-001). As rotas, os corpos e os
status de hoje não mudam. Decisões em [research.md](../research.md), R3, R4 e R13.

## Roteamento por prefixo

| Prefixo | Serviço | Etapa em que muda de destino |
|---|---|---|
| `/admin/sales/**` | `sales` | US2 |
| `/payments/**` | `payment` | US3 |
| `/products/**` | `catalog` | US4 |
| `/cart/**` | `cart` | US5 |
| `/auth/**`, `/users/**` | `identity` | US6 |
| `/orders/**` | `orders` | US7 |

Antes da etapa indicada, o prefixo vai para o `monolith`. A troca de destino é uma
mudança de configuração do gateway, sem mudança para o cliente.

O gateway só roteia os prefixos da API. Não há rota padrão: um caminho fora da
tabela recebe 404 em `ErrorResponse` da própria entrada. Uma rota `/**` para o
`monolith` exporia também as rotas internas e tomaria a página de documentação do
próprio gateway.

## Rotas que o gateway não repassa

| Rota | Motivo |
|---|---|
| `/internal/**` | rotas entre serviços, só na rede interna |
| `/.well-known/jwks.json` | consultada pelos serviços na rede interna |
| `/actuator/**` dos serviços | saúde de cada serviço, só na rede interna |

Nenhuma delas está na tabela de prefixos, então uma requisição a elas pela entrada
única recebe 404 em `ErrorResponse`. O `/actuator/health` do próprio gateway
responde normalmente.

## Documentação

| Rota | Conteúdo |
|---|---|
| `/swagger-ui.html` | página única, com uma entrada por serviço (FR-020) |
| `/v3/api-docs/{serviço}` | documento OpenAPI do serviço, buscado pela entrada única |

As rotas que a entrada não repassa ficam fora dos documentos dos serviços
(`springdoc.paths-to-exclude`), para que a página não mostre rota que responde 404.

## Erros da própria entrada

Quando o serviço de destino não responde, a entrada devolve `ErrorResponse` no
formato de sempre (`status`, `message`, `errors`):

| Situação | Status | `message` |
|---|---|---|
| conexão recusada ou serviço fora do ar | 503 | `Service unavailable` |
| tempo de resposta esgotado (4,5 s) | 504 | `Service did not respond in time` |

O gateway preserva o cabeçalho `Host` da requisição. O serviço monta o `Location`
das respostas 201 e o endereço da documentação como montava antes da entrada
única, sem confiar em `X-Forwarded-*`, que o gateway descarta quando vêm do
cliente. O cliente HTTP do gateway é o Apache HttpClient 5: o do JDK proíbe definir
o `Host` e tenta subir para HTTP/2 (h2c), o que cancela chamadas com corpo.

Erros produzidos pelos serviços (400, 401, 403, 404, 409) passam sem alteração.

O tempo de espera de 4,5 s fica abaixo dos 5 s do SC-003 e acima do prazo de 4 s
da finalização no `cart`, para que, no caso comum, o cliente receba o 503 do
serviço e não um 504 da entrada. Se a entrada responder 504 antes, o carrinho fica
como estava e a repetição devolve o mesmo pedido ([research.md](../research.md),
R6).

## Credencial

A entrada repassa o cabeçalho `Authorization` sem validar o token. Cada serviço
valida a credencial e aplica as regras de acesso (FR-005, FR-014). Um serviço
alcançado diretamente, sem a entrada, recusa a requisição sem credencial válida
(SC-009).
