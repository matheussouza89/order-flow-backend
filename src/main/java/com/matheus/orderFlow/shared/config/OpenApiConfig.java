package com.matheus.orderFlow.shared.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
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
                title = "OrderFlow API",
                version = "1.0.0",
                contact = @Contact(name = "Matheus Souza"),
                description = """
                        ## Purpose
                        REST API for managing a product catalog and customer orders, covering
                        accounts, the shopping cart, order creation, the order lifecycle up to
                        delivery, and the charge registered for each confirmed order.

                        ## Context
                        An order is a historical document, not a live view of the catalog. When
                        an order is created, the name and price of each product are copied into
                        the order item and never change again: updating a product does not
                        rewrite past sales. As a consequence, the item stores the product
                        identifier without a foreign key, and an order remains readable even if
                        the product is removed from the catalog.

                        The client sends only the product and the quantity. Unit price,
                        subtotal, total and status are derived by the domain — there is no way
                        to submit an order whose total disagrees with its items, or one that is
                        already delivered.

                        ## Accounts
                        `POST /auth/register` creates an account and returns **201** with the
                        new user's URI in `Location`.

                        The role is decided by the system and cannot be sent by the client, so
                        registering never produces an administrator — the same reasoning that
                        keeps price and status out of the order payload.

                        Passwords are stored only as a BCrypt hash and never appear in any
                        response. A password must have at least 8 characters, checked before
                        hashing.

                        ## Cart
                        The cart belongs to the authenticated caller: there is no cart id in the
                        URL, and `/cart` always means *your* cart.

                        It is the opposite of the order: it holds only the product and the
                        quantity, and reads name, price and total from the catalog on every
                        request. A cart left open for days shows today's price, while an order
                        keeps the price that applied when it was placed.

                        A cart is created by its first item — there is no endpoint to create an
                        empty one. It expires after seven days without changes.

                        Checkout is the boundary between the two: it creates the order, freezing
                        the current prices, and only then discards the cart.

                        If a product is removed from the catalog, its item disappears from the
                        cart and the total is recalculated without it, instead of the whole cart
                        failing. Checkout is stricter: it refuses with **409 Conflict**, naming
                        every unavailable product, rather than placing an order that differs from
                        what the client reviewed. The cart is left untouched, so removing the
                        item and trying again is enough.

                        ## Order lifecycle
                        An order starts as PENDING and moves forward through business actions:

                        - `confirm` — from PENDING
                        - `ship` — from CONFIRMED
                        - `deliver` — from SHIPPED
                        - `cancel` — from PENDING or CONFIRMED

                        No operation accepts a target status: the client requests an action and
                        the domain decides the outcome. A transition outside these rules returns
                        **409 Conflict** — the request is well formed and the order is valid;
                        what prevents the operation is the current state of the resource.

                        ## Payments
                        Confirming an order registers a charge for it. The payment is created
                        asynchronously — the confirmation responds immediately and the charge
                        appears shortly after — so a query right after confirming may still
                        return 404.

                        There is no endpoint to create, approve or decline a payment. The
                        amount comes from the order and the outcome comes from the payment
                        gateway, so neither can be supplied by the caller. A payment starts as
                        PENDING and settles as:

                        - `APPROVED` — the gateway accepted the charge
                        - `DECLINED` — the gateway refused it; retrying as-is will not help
                        - `FAILED` — a technical failure, such as a timeout; may be retried

                        Confirming the same order twice never produces two charges: the
                        idempotency key is derived from the order.

                        ## Known flows
                        1. The client lists the catalog with `GET /products`, fills a cart with
                           `POST /carts/{cartId}/items` and turns it into an order with
                           `POST /carts/{cartId}/checkout`. The response carries the order with
                           prices already frozen. `POST /orders` does the same in one call, for
                           callers that do not need a cart.
                        2. `POST /orders/{id}/confirm` confirms the order and triggers the
                           charge. The payment can be followed with
                           `GET /payments/orders/{orderId}`. As shipping progresses, the
                           application calls `ship` and `deliver`.
                        3. Before shipping, an order can be cancelled with
                           `POST /orders/{id}/cancel`. After shipping the operation is refused:
                           that case becomes a return, which is out of scope for this version.

                        ## Pagination
                        `GET /products` and `GET /orders` return a page rather than the whole
                        table, with `page`, `size`, `totalElements` and `totalPages` alongside
                        the `content`.

                        They accept `page`, `size` and `sort` (as `field,asc` or `field,desc`).
                        The default is 20 items per page, newest first; `size` is capped at 100,
                        and a larger value is reduced rather than rejected.

                        Asking for a page beyond the last one returns **200** with an empty
                        `content` — the collection exists, the slice is simply empty.

                        ## Authentication
                        `POST /auth/login` returns a JWT valid for one hour. Send it as
                        `Authorization: Bearer <token>` — in Swagger UI, use the **Authorize**
                        button.

                        Browsing the catalog (`GET /products`), registering and logging in need
                        no token. Everything else does. Changing the catalog additionally
                        requires the `ADMIN` role.

                        ## Who owns what
                        Orders, carts and payments belong to the user who created them. A caller
                        only ever reaches their own; an administrator reaches all of them.

                        Asking for someone else's order or payment answers **404**, not 403: a
                        403 would confirm that the identifier exists, which is enough to count
                        the store's orders by trying identifiers.

                        Order actions split by who is entitled to them. `confirm` and `cancel`
                        are decisions of the buyer. `ship` and `deliver` record what logistics
                        did, so they require the `ADMIN` role — otherwise a customer could mark
                        their own order as delivered.

                        Two statuses that are easy to confuse:

                        - **401** — no token, or a token that is invalid, tampered with or expired
                        - **403** — a valid token whose role is not allowed to do this

                        A login failure always answers the same way, whether the email is unknown
                        or the password is wrong, so the endpoint cannot be used to find out who
                        has an account.

                        The token carries only the user id and the role. Its payload is encoded,
                        not encrypted — anyone holding the token can read it — so no personal
                        data is placed in it. A token cannot be revoked before it expires.

                        ## Errors
                        Every error response shares the same body (`ErrorResponse`), with
                        `status`, `message` and `errors`. The `errors` map is filled only when
                        there is a request field to blame — a 409 or a 500 has no offending
                        field and comes with an empty map.
                        """
        )
)
public class OpenApiConfig {
}
