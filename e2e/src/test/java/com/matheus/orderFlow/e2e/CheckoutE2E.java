package com.matheus.orderFlow.e2e;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CheckoutE2E extends AbstractE2E {

    private static Customer customerWithCart(int quantity) {
        Customer customer = newCustomer();
        String productId = createProduct(adminBearer(), "Produto-" + UUID.randomUUID(), "25.00");

        HttpResponse<String> added = post("/cart/items", customer.bearer(), """
                { "productId": "%s", "quantity": %d }
                """.formatted(productId, quantity));
        assertEquals(200, added.statusCode(), added.body());

        return customer;
    }

    @Test
    void shouldCreateTheOrderAndEmptyTheCartThroughTheSingleEntry() {
        Customer customer = customerWithCart(2);

        HttpResponse<String> checkout = post("/cart/checkout", customer.bearer(), "");
        assertEquals(200, checkout.statusCode(), checkout.body());
        String orderId = read(checkout, "$.id");

        HttpResponse<String> order = get("/orders/" + orderId, customer.bearer());
        assertEquals(200, order.statusCode(), order.body());
        assertEquals(50.00, ((Number) read(order, "$.total")).doubleValue());
        assertEquals(404, get("/cart", customer.bearer()).statusCode());
    }

    @Test
    void shouldRefuseTheCheckoutInTimeAndKeepTheCartWhileOrdersIsDown() {
        Customer customer = customerWithCart(3);
        String cart = get("/cart", customer.bearer()).body();

        stop("orders");
        try {
            Instant start = Instant.now();
            HttpResponse<String> checkout = post("/cart/checkout", customer.bearer(), "");
            Duration elapsed = Duration.between(start, Instant.now());

            assertThat(checkout.statusCode()).as(checkout.body()).isIn(503, 504);
            assertEquals(checkout.statusCode(), (int) read(checkout, "$.status"));
            assertThat((String) read(checkout, "$.message")).isNotBlank();
            assertThat(elapsed).isLessThan(Duration.ofSeconds(5));
            assertEquals(cart, get("/cart", customer.bearer()).body());
        } finally {
            start("orders");
        }

        HttpResponse<String> retried = Awaitility.await()
                .atMost(Duration.ofSeconds(90))
                .pollInterval(Duration.ofSeconds(2))
                .ignoreExceptions()
                .until(() -> post("/cart/checkout", customer.bearer(), ""),
                        response -> response.statusCode() == 200);
        String orderId = read(retried, "$.id");

        HttpResponse<String> orders = get("/orders", customer.bearer());
        assertEquals(200, orders.statusCode(), orders.body());
        assertEquals(1, (int) read(orders, "$.totalElements"));
        assertEquals(orderId, read(orders, "$.content[0].id"));
    }

    @Test
    void shouldListTheOrdersWhileTheCartIsDown() {
        Customer customer = customerWithCart(1);
        assertEquals(200, post("/cart/checkout", customer.bearer(), "").statusCode());

        stop("cart");
        try {
            HttpResponse<String> orders = get("/orders", customer.bearer());

            assertEquals(200, orders.statusCode(), orders.body());
            assertEquals(1, (int) read(orders, "$.totalElements"));
        } finally {
            start("cart");
        }

        Awaitility.await()
                .atMost(Duration.ofSeconds(90))
                .pollInterval(Duration.ofSeconds(2))
                .ignoreExceptions()
                .until(() -> get("/cart", customer.bearer()).statusCode() == 404);
    }
}
