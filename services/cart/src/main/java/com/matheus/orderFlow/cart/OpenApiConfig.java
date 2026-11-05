package com.matheus.orderFlow.cart;

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
                title = "OrderFlow Cart API",
                version = "1.0.0",
                contact = @Contact(name = "Matheus Souza"),
                description = """
                        ## Purpose
                        The shopping cart and its checkout, which turns the cart into an order.

                        ## Whose cart
                        The cart belongs to the authenticated caller: there is no cart id in the
                        URL, and `/cart` always means *your* cart. Every operation needs a token.

                        ## Today's price
                        The cart holds only the product and the quantity, and reads name, price
                        and total from the catalog on every request. A cart left open for days
                        shows today's price, while an order keeps the price that applied when it
                        was placed.

                        A cart is created by its first item — there is no endpoint to create an
                        empty one. It expires after seven days without changes.

                        If a product is removed from the catalog, its item disappears from the
                        cart and the total is recalculated without it. Checkout is stricter: it
                        refuses with **409 Conflict**, naming every unavailable product, and the
                        cart is left untouched.

                        ## Checkout
                        Checkout checks every item against the catalog, asks the orders service
                        to create the order, and only then discards the cart. The response is the
                        order as the orders service returned it.

                        Checkout answers within 4 seconds. If the catalog or the orders service
                        is down, slow, or the 4 seconds run out, it responds **503** and the cart
                        stays as it was. Repeating the checkout is always safe: every attempt on
                        the same cart carries the same key, so an order that was created while
                        the response was lost comes back instead of a second one.

                        A refusal from the orders service, such as **401** for an expired token,
                        reaches the caller with the same status and body.

                        ## Errors
                        Every error response shares the same body (`ErrorResponse`), with
                        `status`, `message` and `errors`. The `errors` map is filled only when
                        there is a request field to blame.
                        """
        )
)
class OpenApiConfig {
}
