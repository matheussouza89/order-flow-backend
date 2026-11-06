package com.matheus.orderFlow.e2e;

import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
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
        return placeOrder(customer, createProduct(adminBearer(), "Produto-" + UUID.randomUUID(), price), quantity);
    }
}
