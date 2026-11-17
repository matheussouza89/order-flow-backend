package com.matheus.orderFlow.e2e;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FailureIsolationE2E extends AbstractE2E {

    private static String act(String orderId, String action, String bearer) {
        return read(post("/orders/" + orderId + "/" + action, bearer, ""), "$.status");
    }

    @Test
    void shouldKeepTheOrdersWorkingWithPaymentsSalesAndNotificationsDownAtTheSameTime() {
        String admin = adminBearer();
        Customer customer = newCustomer();
        String productId = createProduct(admin, "Produto-" + UUID.randomUUID(), "10.00");

        stop("payment");
        stop("sales");
        stop("notifications");
        try {
            String delivered = placeOrder(customer, productId, 1);
            assertEquals("CONFIRMED", act(delivered, "confirm", customer.bearer()));
            assertEquals("SHIPPED", act(delivered, "ship", admin));
            assertEquals("DELIVERED", act(delivered, "deliver", admin));

            String cancelled = placeOrder(customer, productId, 1);
            assertEquals("CONFIRMED", act(cancelled, "confirm", customer.bearer()));
            assertEquals("CANCELLED", act(cancelled, "cancel", customer.bearer()));
        } finally {
            start("payment");
            start("sales");
            start("notifications");
        }
    }
}
