package com.matheus.orderFlow.payment;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Paste the token returned by POST /auth/login."
)
@OpenAPIDefinition(
        security = @SecurityRequirement(name = "bearerAuth"),
        info = @Info(
                title = "OrderFlow Payments API",
                version = "1.0.0",
                contact = @Contact(name = "Matheus Souza"),
                description = """
                        ## Purpose
                        The charge registered for each confirmed order, and its outcome at the
                        payment gateway. Every route requires a token.

                        ## Where a payment comes from
                        This service learns about a confirmation from the fact the orders service
                        publishes, never from a call. The payment is created asynchronously — the
                        confirmation responds immediately and the charge appears shortly after —
                        so a query right after confirming may still return 404. If this service is
                        down, orders are still confirmed normally, and the charge happens when it
                        comes back.

                        There is no endpoint to create, approve or decline a payment. The amount
                        comes from the order and the outcome comes from the payment gateway, so
                        neither can be supplied by the caller. A payment starts as PENDING and
                        settles as:

                        - `APPROVED` — the gateway accepted the charge
                        - `DECLINED` — the gateway refused it; retrying as-is will not help
                        - `FAILED` — a technical failure, such as a timeout; may be retried

                        The same confirmation never produces two charges, even if it arrives
                        twice: the idempotency key is derived from the order.

                        ## Who owns what
                        A payment belongs to the buyer of the order. A caller only ever reaches
                        their own; an administrator reaches all of them. Asking for someone else's
                        payment answers **404**, not 403, so the endpoint cannot be used to find
                        out which identifiers exist.

                        ## Errors
                        Every error response shares the same body (`ErrorResponse`), with
                        `status`, `message` and `errors`. **401** means no token or an invalid one.
                        """
        )
)
class OpenApiConfig {
}
