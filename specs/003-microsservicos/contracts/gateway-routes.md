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
| qualquer outro | `monolith` | até a US7, quando deixa de existir |

Antes da etapa indicada, o prefixo vai para o `monolith`. A troca de destino é uma
mudança de configuração do gateway, sem mudança para o cliente.

## Rotas que o gateway não repassa

| Rota | Motivo |
|---|---|
| `/internal/**` | rotas entre serviços, só na rede interna |
| `/.well-known/jwks.json` | consultada pelos serviços na rede interna |
| `/actuator/**` dos serviços | saúde de cada serviço, só na rede interna |

Uma requisição a essas rotas pela entrada única recebe 404 em `ErrorResponse`.

## Documentação

| Rota | Conteúdo |
|---|---|
| `/swagger-ui.html` | página única, com uma entrada por serviço (FR-020) |
| `/v3/api-docs/{serviço}` | documento OpenAPI do serviço, buscado pela entrada única |

## Erros da própria entrada

Quando o serviço de destino não responde, a entrada devolve `ErrorResponse` no
formato de sempre (`status`, `message`, `errors`):

| Situação | Status | `message` |
|---|---|---|
| conexão recusada ou serviço fora do ar | 503 | serviço indisponível |
| tempo de resposta esgotado | 504 | serviço não respondeu a tempo |

Erros produzidos pelos serviços (400, 401, 403, 404, 409) passam sem alteração.

## Credencial

A entrada repassa o cabeçalho `Authorization` sem validar o token. Cada serviço
valida a credencial e aplica as regras de acesso (FR-005, FR-014). Um serviço
alcançado diretamente, sem a entrada, recusa a requisição sem credencial válida
(SC-009).
