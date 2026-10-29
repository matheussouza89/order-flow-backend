package com.matheus.orderFlow.contracts.order;

import java.util.Map;

public final class OrderFacts {
    public static final String CONFIRMED_KEY = "order.confirmed";
    public static final String SHIPPED_KEY = "order.shipped";
    public static final String DELIVERED_KEY = "order.delivered";
    public static final String CANCELLED_KEY = "order.cancelled";

    public static final String CONFIRMED_V1 = "order.confirmed.v1";
    public static final String SHIPPED_V1 = "order.shipped.v1";
    public static final String DELIVERED_V1 = "order.delivered.v1";
    public static final String CANCELLED_V1 = "order.cancelled.v1";

    public static final Map<String, Class<?>> TYPES = Map.of(
            CONFIRMED_V1, OrderConfirmedEvent.class,
            SHIPPED_V1, OrderShippedEvent.class,
            DELIVERED_V1, OrderDeliveredEvent.class,
            CANCELLED_V1, OrderCancelledEvent.class
    );

    public static final Map<String, Class<?>> LEGACY_TYPES = Map.of(
            "com.matheus.orderFlow.order.OrderConfirmedEvent", OrderConfirmedEvent.class,
            "com.matheus.orderFlow.order.OrderShippedEvent", OrderShippedEvent.class,
            "com.matheus.orderFlow.order.OrderDeliveredEvent", OrderDeliveredEvent.class,
            "com.matheus.orderFlow.order.OrderCancelledEvent", OrderCancelledEvent.class
    );

    private OrderFacts() {
    }
}
