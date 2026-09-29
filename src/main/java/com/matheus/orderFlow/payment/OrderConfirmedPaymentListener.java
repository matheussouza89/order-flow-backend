package com.matheus.orderFlow.payment;

import com.matheus.orderFlow.order.OrderConfirmedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class OrderConfirmedPaymentListener {
    private final PaymentService paymentService;

    @RabbitListener(queues = PaymentMessagingConfig.ORDER_CONFIRMED_QUEUE)
    void onOrderConfirmed(OrderConfirmedEvent event) {
        paymentService.chargeOrder(event.orderId(), event.total());
    }
}
