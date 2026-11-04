package com.matheus.orderFlow.e2e;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CatalogOutageE2E extends AbstractE2E {

    @Test
    void shouldShowTheStorefrontThroughTheSingleEntry() {
        String name = "Produto-" + UUID.randomUUID();
        String productId = createProduct(adminBearer(), name, "19.90");

        HttpResponse<String> product = get("/products/" + productId, null);
        assertEquals(200, product.statusCode(), product.body());
        assertEquals(name, read(product, "$.name"));

        HttpResponse<String> storefront = get("/products", null);
        assertEquals(200, storefront.statusCode(), storefront.body());
    }

    @Test
    void shouldRefuseTheCheckoutInTimeAndKeepTheCartWhileTheCatalogIsDown() {
        Customer customer = newCustomer();
        String productId = createProduct(adminBearer(), "Produto-" + UUID.randomUUID(), "30.00");
        String item = """
                { "productId": "%s", "quantity": 2 }
                """.formatted(productId);
        assertEquals(201, post("/orders", customer.bearer(), "{ \"items\": [ " + item + " ] }").statusCode());
        assertEquals(200, post("/cart/items", customer.bearer(), item).statusCode());
        String cart = get("/cart", customer.bearer()).body();

        stop("catalog");
        try {
            Instant start = Instant.now();
            HttpResponse<String> checkout = post("/cart/checkout", customer.bearer(), "");
            Duration elapsed = Duration.between(start, Instant.now());

            assertEquals(503, checkout.statusCode(), checkout.body());
            assertEquals("Service unavailable: catalog", read(checkout, "$.message"));
            assertThat(elapsed).isLessThan(Duration.ofSeconds(5));

            HttpResponse<String> orders = get("/orders", customer.bearer());
            assertEquals(200, orders.statusCode(), orders.body());
            assertEquals(1, (int) read(orders, "$.totalElements"));
        } finally {
            start("catalog");
        }

        Awaitility.await()
                .atMost(Duration.ofSeconds(90))
                .pollInterval(Duration.ofSeconds(2))
                .ignoreExceptions()
                .until(() -> get("/products/" + productId, null).statusCode() == 200
                        && cart.equals(get("/cart", customer.bearer()).body()));
        assertEquals(cart, get("/cart", customer.bearer()).body());
    }
}
