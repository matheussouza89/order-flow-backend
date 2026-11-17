package com.matheus.orderFlow.integration;

import com.matheus.orderFlow.contact.ContactService;
import com.matheus.orderFlow.contracts.order.OrderCancelledEvent;
import com.matheus.orderFlow.contracts.order.OrderFacts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderCancelledNotificationIT extends AbstractIntegrationTest {
    private static final String EMAIL = "ana@example.com";

    @Autowired
    private ContactService contactService;

    @Test
    void shouldEmailWhenTheOrderIsCancelled() {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        contactService.record(userId, EMAIL, Instant.now());

        publish(OrderFacts.CANCELLED_KEY, new OrderCancelledEvent(orderId, userId, 3L, Instant.now()));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertEquals(1, mailsTo(EMAIL).size()));
        Mail mail = mailsTo(EMAIL).getFirst();
        assertEquals("Pedido " + orderId + " cancelado", mail.subject());
        assertEquals("Seu pedido " + orderId + " foi cancelado.", mail.text().strip());
    }
}
