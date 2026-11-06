package com.matheus.orderFlow.e2e;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FullPurchaseE2E extends AbstractE2E {

    @Test
    void shouldCompleteAPurchaseFromSignUpToTheSalesReportThroughTheSingleEntry() {
        String admin = adminBearer();
        String product = "Produto-" + UUID.randomUUID();
        String productId = createProduct(admin, product, "35.00");
        Customer customer = newCustomer();

        HttpResponse<String> storefront = get("/products/" + productId, null);
        assertEquals(200, storefront.statusCode(), storefront.body());

        HttpResponse<String> added = post("/cart/items", customer.bearer(), """
                { "productId": "%s", "quantity": 3 }
                """.formatted(productId));
        assertEquals(200, added.statusCode(), added.body());

        HttpResponse<String> checkout = post("/cart/checkout", customer.bearer(), "");
        assertEquals(200, checkout.statusCode(), checkout.body());
        String orderId = read(checkout, "$.id");
        assertEquals(105.00, ((Number) read(checkout, "$.total")).doubleValue());

        assertEquals("CONFIRMED", read(post("/orders/" + orderId + "/confirm", customer.bearer(), ""), "$.status"));
        HttpResponse<String> payment = awaitApproval(customer, orderId);
        assertEquals(105.00, ((Number) read(payment, "$.amount")).doubleValue());

        assertEquals("SHIPPED", read(post("/orders/" + orderId + "/ship", admin, ""), "$.status"));
        assertEquals("DELIVERED", read(post("/orders/" + orderId + "/deliver", admin, ""), "$.status"));
        assertEquals("DELIVERED", read(get("/orders/" + orderId, customer.bearer()), "$.status"));

        Awaitility.await()
                .atMost(Duration.ofSeconds(60))
                .ignoreExceptions()
                .until(() -> quantitySold(admin, product), quantity -> quantity == 3);
    }
}
