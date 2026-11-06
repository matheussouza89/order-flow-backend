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
                        REST API for customer orders, covering order creation and the order
                        lifecycle up to delivery.

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

                        ## Catalog
                        The products under `/products` are served by their own service and
                        documented in its entry on this page. Creating an order reads name and
                        price from it while the request runs. If the catalog does not answer
                        within 1.5 seconds, the request responds **503** and creates nothing, and
                        listing your own orders keeps working.

                        ## Accounts
                        Registering, signing in and the tokens under `/auth` and `/users` are
                        served by their own service and documented in its entry on this page.

                        ## Cart
                        The cart under `/cart` is served by its own service and documented in its
                        entry on this page. Its checkout creates the order with `POST /orders`.

                        ## Repeating an order
                        `POST /orders` accepts an optional `Idempotency-Key` of up to 64
                        characters. Repeating the request with the same key returns the order
                        already created for it — **201** with the same `Location` and body —
                        instead of creating another one. Keys are independent per user, and
                        without the header every request creates an order. The checkout sends
                        one, so an order whose response was lost is not placed twice.

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
                        Confirming an order registers a charge for it. The payments under
                        `/payments` are served by their own service and documented in its entry
                        on this page.

                        ## Known flows
                        1. The client registers with `POST /auth/register` and signs in with
                           `POST /auth/login`, which returns the token pair. Browsing the
                           catalog with `GET /products` needs no token; everything below does.
                        2. The client fills a cart with `POST /cart/items` and turns it into an
                           order with `POST /cart/checkout`. The response carries the order with
                           prices already frozen. `POST /orders` does the same in one call, for
                           callers that do not need a cart.
                        3. `POST /orders/{id}/confirm` confirms the order and triggers the
                           charge. The payment can be followed with
                           `GET /payments/orders/{orderId}`. As shipping progresses, an
                           administrator calls `ship` and `deliver`.
                        4. Before shipping, the buyer can cancel with
                           `POST /orders/{id}/cancel`. After shipping the operation is refused:
                           that case becomes a return, which is out of scope for this version.
                        5. Once the access token expires, `POST /auth/refresh` returns a new
                           pair without asking for the password again.

                        ## Pagination
                        `GET /orders` returns a page rather than the whole table, with `page`,
                        `size`, `totalElements` and `totalPages` alongside the `content`.

                        It accepts `page`, `size` and `sort` (as `field,asc` or `field,desc`).
                        The default is 20 items per page, newest first; `size` is capped at 100,
                        and a larger value is reduced rather than rejected.

                        Asking for a page beyond the last one returns **200** with an empty
                        `content` — the collection exists, the slice is simply empty.

                        ## Authentication
                        Every route here needs the access token returned by `POST /auth/login`,
                        sent as `Authorization: Bearer <token>`. In Swagger UI, use the
                        **Authorize** button. This service only verifies the token, with the
                        public keys of the service that issues it.

                        ## Who owns what
                        Orders belong to the user who created them. A caller only ever
                        reaches their own; an administrator reaches all of them.

                        Asking for someone else's order answers **404**, not 403: a
                        403 would confirm that the identifier exists, which is enough to count
                        the store's orders by trying identifiers.

                        Order actions split by who is entitled to them. `confirm` and `cancel`
                        are decisions of the buyer. `ship` and `deliver` record what logistics
                        did, so they require the `ADMIN` role — otherwise a customer could mark
                        their own order as delivered.

                        Two statuses that are easy to confuse:

                        - **401** — no token, or a token that is invalid, tampered with or expired
                        - **403** — a valid token whose role is not allowed to do this

                        ## Sales reports
                        The sales reports under `/admin/sales` are served by their own service
                        and documented in its entry on this page.

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
