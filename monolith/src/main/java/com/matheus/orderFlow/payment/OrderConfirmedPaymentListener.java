package com.matheus.orderFlow.payment;

import com.matheus.orderFlow.order.OrderConfirmedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class OrderConfirmedPaymentListener {
    private final PaymentProcessor paymentProcessor;

    @RabbitListener(queues = PaymentMessagingConfig.ORDER_CONFIRMED_QUEUE)
    void onOrderConfirmed(OrderConfirmedEvent event) {
        paymentProcessor.process(event.orderId(), event.userId(), event.total());
    }
}
