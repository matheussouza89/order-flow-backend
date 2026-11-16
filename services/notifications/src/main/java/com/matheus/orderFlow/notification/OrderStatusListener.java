package com.matheus.orderFlow.notification;

import com.matheus.orderFlow.contracts.order.OrderConfirmedEvent;
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
}
