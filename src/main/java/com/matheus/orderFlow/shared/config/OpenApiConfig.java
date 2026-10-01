package com.matheus.orderFlow.shared.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "OrderFlow API",
                version = "1.0.0",
                contact = @Contact(name = "Matheus Souza"),
                description = """
                        ## Purpose
                        REST API for managing a product catalog and customer orders, covering
                        the shopping cart, order creation, the order lifecycle up to delivery,
                        and the charge registered for each confirmed order.

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

                        ## Cart
                        The cart is the opposite of the order: it holds only the product and the
                        quantity, and reads name, price and total from the catalog on every
                        request. A cart left open for days shows today's price, while an order
                        keeps the price that applied when it was placed.

                        A cart is created by its first item — there is no endpoint to create an
                        empty one — and is identified by a caller-supplied id. It expires after
                        seven days without changes.

                        Checkout is the boundary between the two: it creates the order, freezing
                        the current prices, and only then discards the cart. If a product is
                        removed from the catalog, its item disappears from the cart and the total
                        is recalculated without it, instead of the whole cart failing.

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
