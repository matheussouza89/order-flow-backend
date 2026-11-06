package com.matheus.orderFlow.e2e;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderFactsDeliveryE2E extends AbstractE2E {

    private static String statusInTheSalesView(String orderId) {
        Container.ExecResult result = runIn("mongo", "mongosh", "--quiet", "orderflow",
                "--eval", "db.order_views.findOne({ _id: '%s' })?.status".formatted(orderId));
        assertEquals(0, result.getExitCode(), result.getStderr());

        return result.getStdout().strip();
    }

    private static String act(String orderId, String action, String bearer) {
        return read(post("/orders/" + orderId + "/" + action, bearer, ""), "$.status");
    }

    @Test
    void shouldDeliverEveryFactOnceToTheConsumersThatWereDownWhenTheyReturn() {
        String admin = adminBearer();
        Customer customer = newCustomer();
        String deliveredProduct = "Produto-" + UUID.randomUUID();
        String cancelledProduct = "Produto-" + UUID.randomUUID();
        String delivered = placeOrder(customer, createProduct(admin, deliveredProduct, "20.00"), 2);
        String cancelled = placeOrder(customer, createProduct(admin, cancelledProduct, "30.00"), 5);

        stop("payment");
        stop("sales");
        try {
            assertEquals("CONFIRMED", act(delivered, "confirm", customer.bearer()));
            assertEquals("SHIPPED", act(delivered, "ship", admin));
            assertEquals("DELIVERED", act(delivered, "deliver", admin));
            assertEquals("CONFIRMED", act(cancelled, "confirm", customer.bearer()));
            assertEquals("CANCELLED", act(cancelled, "cancel", customer.bearer()));
        } finally {
            start("payment");
            start("sales");
        }

        Awaitility.await()
                .atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofSeconds(2))
                .ignoreExceptions()
                .until(() -> statusInTheSalesView(delivered).equals("DELIVERED")
                        && statusInTheSalesView(cancelled).equals("CANCELLED"));
        awaitApproval(customer, delivered);
        awaitApproval(customer, cancelled);

        assertEquals(1, chargesFor(delivered));
        assertEquals(1, chargesFor(cancelled));
        assertEquals(2L, quantitySold(admin, deliveredProduct));
        assertEquals(0L, quantitySold(admin, cancelledProduct));
    }
}
