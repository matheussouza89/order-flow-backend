package com.matheus.orderFlow.e2e;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PaymentIsolationE2E extends AbstractE2E {

    @Test
    void shouldConfirmOrdersWhilePaymentsAreDownAndChargeOnceWhenTheyReturn() {
        Customer customer = newCustomer();
        String orderId = order(customer, "40.00", 2);

        stop("payment");
        try {
            HttpResponse<String> confirmed = post("/orders/" + orderId + "/confirm", customer.bearer(), "");
            assertEquals(200, confirmed.statusCode(), confirmed.body());
        } finally {
            start("payment");
        }

        HttpResponse<String> payment = awaitApproval(customer, orderId);

        assertEquals(80.0, ((Number) read(payment, "$.amount")).doubleValue());
        assertEquals(1, chargesFor(orderId));
    }

    @Test
    void shouldAnswerNotFoundToAnotherCustomer() {
        Customer customer = newCustomer();
        String orderId = order(customer, "15.00", 1);
        post("/orders/" + orderId + "/confirm", customer.bearer(), "");
        String paymentId = read(awaitApproval(customer, orderId), "$.id");

        Customer stranger = newCustomer();

        assertEquals(404, get("/payments/" + paymentId, stranger.bearer()).statusCode());
        assertEquals(404, get("/payments/orders/" + orderId, stranger.bearer()).statusCode());
    }

    private static String order(Customer customer, String price, int quantity) {
        String productId = createProduct(adminBearer(), "Produto-" + UUID.randomUUID(), price);
        HttpResponse<String> created = post("/orders", customer.bearer(), """
                { "items": [ { "productId": "%s", "quantity": %d } ] }
                """.formatted(productId, quantity));
        assertEquals(201, created.statusCode(), created.body());

        return read(created, "$.id");
    }

    private static HttpResponse<String> awaitApproval(Customer customer, String orderId) {
        return Awaitility.await()
                .atMost(Duration.ofSeconds(60))
                .ignoreExceptions()
                .until(() -> get("/payments/orders/" + orderId, customer.bearer()),
                        response -> response.statusCode() == 200 && "APPROVED".equals(read(response, "$.status")));
    }

    private static int chargesFor(String orderId) {
        HttpResponse<String> count = send(HttpRequest.newBuilder(service("wiremock", "/__admin/requests/count"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""
                        { "method": "POST", "url": "/charges",
                          "headers": { "Idempotency-Key": { "equalTo": "order-%s" } } }
                        """.formatted(orderId))));
        assertEquals(200, count.statusCode(), count.body());

        return read(count, "$.count");
    }
}
