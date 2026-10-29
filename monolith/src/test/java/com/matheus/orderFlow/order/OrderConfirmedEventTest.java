package com.matheus.orderFlow.order;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNull;

class OrderConfirmedEventTest {

    @Test
    void shouldReadMessagesPublishedBeforeItemsAndVersionExisted() {
        String payload = """
                {
                    "orderId": "%s",
                    "userId": "%s",
                    "total": 100.00,
                    "occurredAt": "2026-10-14T12:00:00Z"
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID());

        OrderConfirmedEvent event = JsonMapper.builder().build()
                .readValue(payload, OrderConfirmedEvent.class);

        assertNull(event.items());
        assertNull(event.version());
    }
}
