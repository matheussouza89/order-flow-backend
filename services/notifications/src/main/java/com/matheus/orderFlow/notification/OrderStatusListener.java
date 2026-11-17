package com.matheus.orderFlow.notification;

import com.matheus.orderFlow.contracts.order.OrderCancelledEvent;
import com.matheus.orderFlow.contracts.order.OrderConfirmedEvent;
import com.matheus.orderFlow.contracts.order.OrderDeliveredEvent;
import com.matheus.orderFlow.contracts.order.OrderShippedEvent;
import com.matheus.orderFlow.shared.messaging.RabbitMqConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@RabbitListener(queues = NotificationMessagingConfig.ORDER_STATUS_QUEUE,
        containerFactory = RabbitMqConfig.RESILIENT_LISTENER_FACTORY)
class OrderStatusListener {
    private final NotificationService notificationService;

    @RabbitHandler
    void on(OrderConfirmedEvent event) {
        notificationService.register(event.orderId(), OrderStatus.CONFIRMED, event.version(), event.userId(),
                event.total());
    }

    @RabbitHandler
    void on(OrderShippedEvent event) {
        notificationService.register(event.orderId(), OrderStatus.SHIPPED, event.version(), event.userId(), null);
    }

    @RabbitHandler
    void on(OrderDeliveredEvent event) {
        notificationService.register(event.orderId(), OrderStatus.DELIVERED, event.version(), event.userId(), null);
    }

    @RabbitHandler
    void on(OrderCancelledEvent event) {
        notificationService.register(event.orderId(), OrderStatus.CANCELLED, event.version(), event.userId(), null);
    }
}
