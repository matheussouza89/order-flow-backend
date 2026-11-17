package com.matheus.orderFlow.integration;

import com.matheus.orderFlow.contact.ContactService;
import com.matheus.orderFlow.contracts.order.OrderDeliveredEvent;
import com.matheus.orderFlow.contracts.order.OrderFacts;
import com.matheus.orderFlow.contracts.order.OrderShippedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderProgressNotificationIT extends AbstractIntegrationTest {
    private static final String EMAIL = "ana@example.com";

    @Autowired
    private ContactService contactService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final UUID userId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();

    @BeforeEach
    void knowTheCustomer() {
        contactService.record(userId, EMAIL, Instant.now());
    }

    private void ship() {
        publish(OrderFacts.SHIPPED_KEY, new OrderShippedEvent(orderId, userId, 2L, Instant.now()));
    }

    private void deliver() {
        publish(OrderFacts.DELIVERED_KEY, new OrderDeliveredEvent(orderId, userId, 3L, Instant.now()));
    }

    private String stateOf(String orderStatus) {
        return jdbcTemplate.queryForObject(
                "SELECT state FROM notifications WHERE order_id = UUID_TO_BIN(?) AND order_status = ?",
                String.class, orderId.toString(), orderStatus);
    }

    @Test
    void shouldEmailWhenTheOrderIsShippedAndWhenItIsDelivered() {
        ship();
        deliver();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertEquals(2, mailsTo(EMAIL).size()));
        assertThat(mailsTo(EMAIL)).extracting(Mail::subject).containsExactlyInAnyOrder(
                "Pedido " + orderId + " enviado",
                "Pedido " + orderId + " entregue");
    }

    @Test
    void shouldNotEmailTheShipmentAfterTheDeliveryWasEmailed() {
        deliver();
        await().atMost(Duration.ofSeconds(10)).ignoreExceptions()
                .untilAsserted(() -> assertEquals("SENT", stateOf("DELIVERED")));

        ship();

        await().atMost(Duration.ofSeconds(10)).ignoreExceptions()
                .untilAsserted(() -> assertEquals("SUPERSEDED", stateOf("SHIPPED")));
        assertThat(mailsTo(EMAIL)).extracting(Mail::subject).containsExactly("Pedido " + orderId + " entregue");
    }
}
