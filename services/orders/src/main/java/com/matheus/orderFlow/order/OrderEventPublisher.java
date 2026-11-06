package com.matheus.orderFlow.order;

import com.matheus.orderFlow.contracts.order.OrderFacts;
import com.matheus.orderFlow.contracts.order.OrderCancelledEvent;
import com.matheus.orderFlow.contracts.order.OrderConfirmedEvent;
import com.matheus.orderFlow.contracts.order.OrderDeliveredEvent;
import com.matheus.orderFlow.contracts.order.OrderShippedEvent;
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
        outboxWriter.record(OrderFacts.CONFIRMED_KEY, event);
    }

    @EventListener
    void onOrderShipped(OrderShippedEvent event) {
        outboxWriter.record(OrderFacts.SHIPPED_KEY, event);
    }

    @EventListener
    void onOrderDelivered(OrderDeliveredEvent event) {
        outboxWriter.record(OrderFacts.DELIVERED_KEY, event);
    }

    @EventListener
    void onOrderCancelled(OrderCancelledEvent event) {
        outboxWriter.record(OrderFacts.CANCELLED_KEY, event);
    }
}
