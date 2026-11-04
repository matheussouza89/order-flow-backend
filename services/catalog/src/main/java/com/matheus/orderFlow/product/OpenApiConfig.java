package com.matheus.orderFlow.product;

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
                title = "OrderFlow Catalog API",
                version = "1.0.0",
                contact = @Contact(name = "Matheus Souza"),
                description = """
                        ## Purpose
                        The product catalog: the storefront anyone can browse and the products an
                        administrator maintains.

                        ## Who reaches what
                        `GET /products` and `GET /products/{id}` need no token — they are the
                        storefront. Creating, updating and deleting a product require the
                        `ADMIN` role.

                        ## The catalog and past orders
                        A cart reads name and price from this service on every request, so it
                        always shows today's price. An order copies them when it is placed:
                        changing or removing a product here never rewrites an order that already
                        exists.

                        ## Pagination
                        `GET /products` returns a page rather than the whole table, with `page`,
                        `size`, `totalElements` and `totalPages` alongside the `content`. It
                        accepts `page`, `size` and `sort` (as `field,asc` or `field,desc`). The
                        default is 20 items per page, newest first; `size` is capped at 100, and a
                        larger value is reduced rather than rejected. Asking for a page beyond the
                        last one returns **200** with an empty `content`.

                        ## Errors
                        Every error response shares the same body (`ErrorResponse`), with
                        `status`, `message` and `errors`. **401** means no token or an invalid one;
                        **403** means a valid token without the `ADMIN` role.
                        """
        )
)
class OpenApiConfig {
}
