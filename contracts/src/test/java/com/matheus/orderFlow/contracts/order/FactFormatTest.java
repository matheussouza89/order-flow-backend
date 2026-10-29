package com.matheus.orderFlow.contracts.order;

import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FactFormatTest {

    private static Map<String, Class<?>> fieldsOf(Class<? extends Record> fact) {
        return Arrays.stream(fact.getRecordComponents())
                .collect(Collectors.toMap(RecordComponent::getName, RecordComponent::getType));
    }

    @Test
    void shouldKeepEveryFieldOfOrderConfirmedV1() {
        assertThat(fieldsOf(OrderConfirmedEvent.class)).containsAllEntriesOf(Map.of(
                "orderId", UUID.class,
                "userId", UUID.class,
                "total", BigDecimal.class,
                "items", List.class,
                "version", Long.class,
                "occurredAt", Instant.class));
    }

    @Test
    void shouldKeepEveryFieldOfTheConfirmedItem() {
        assertThat(fieldsOf(OrderConfirmedEvent.Item.class)).containsAllEntriesOf(Map.of(
                "productId", UUID.class,
                "productName", String.class,
                "unitPrice", BigDecimal.class,
                "quantity", int.class,
                "subtotal", BigDecimal.class));
    }

    @Test
    void shouldKeepEveryFieldOfTheStatusChangesV1() {
        Map<String, Class<?>> statusChange = Map.of(
                "orderId", UUID.class,
                "version", long.class,
                "occurredAt", Instant.class);

        assertThat(fieldsOf(OrderShippedEvent.class)).containsAllEntriesOf(statusChange);
        assertThat(fieldsOf(OrderDeliveredEvent.class)).containsAllEntriesOf(statusChange);
        assertThat(fieldsOf(OrderCancelledEvent.class)).containsAllEntriesOf(statusChange);
    }

    @Test
    void shouldPublishEachFactUnderItsVersionedId() {
        assertEquals(Map.of(
                "order.confirmed.v1", OrderConfirmedEvent.class,
                "order.shipped.v1", OrderShippedEvent.class,
                "order.delivered.v1", OrderDeliveredEvent.class,
                "order.cancelled.v1", OrderCancelledEvent.class), OrderFacts.TYPES);
    }

    @Test
    void shouldStillReadTheClassNamesUsedBeforeTheVersionedIds() {
        assertEquals(Map.of(
                "com.matheus.orderFlow.order.OrderConfirmedEvent", OrderConfirmedEvent.class,
                "com.matheus.orderFlow.order.OrderShippedEvent", OrderShippedEvent.class,
                "com.matheus.orderFlow.order.OrderDeliveredEvent", OrderDeliveredEvent.class,
                "com.matheus.orderFlow.order.OrderCancelledEvent", OrderCancelledEvent.class),
                OrderFacts.LEGACY_TYPES);
    }

    @Test
    void shouldRouteEachFactByTheSameKeyAsBefore() {
        assertEquals("order.confirmed", OrderFacts.CONFIRMED_KEY);
        assertEquals("order.shipped", OrderFacts.SHIPPED_KEY);
        assertEquals("order.delivered", OrderFacts.DELIVERED_KEY);
        assertEquals("order.cancelled", OrderFacts.CANCELLED_KEY);
    }
}
