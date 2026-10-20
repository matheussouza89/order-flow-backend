package com.matheus.orderFlow.order;

import com.matheus.orderFlow.shared.messaging.RabbitMqConfig;
import com.matheus.orderFlow.shared.outbox.OutboxWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class OrderEventPublisher {
    private final OutboxWriter outboxWriter;

    @EventListener
    void onOrderConfirmed(OrderConfirmedEvent event) {
        outboxWriter.record(RabbitMqConfig.ORDER_CONFIRMED_KEY, event);
    }

    @EventListener
    void onOrderShipped(OrderShippedEvent event) {
        outboxWriter.record(RabbitMqConfig.ORDER_SHIPPED_KEY, event);
    }

    @EventListener
    void onOrderDelivered(OrderDeliveredEvent event) {
        outboxWriter.record(RabbitMqConfig.ORDER_DELIVERED_KEY, event);
    }

    @EventListener
    void onOrderCancelled(OrderCancelledEvent event) {
        outboxWriter.record(RabbitMqConfig.ORDER_CANCELLED_KEY, event);
    }
}
