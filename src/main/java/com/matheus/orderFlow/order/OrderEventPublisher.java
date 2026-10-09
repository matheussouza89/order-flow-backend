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
}
