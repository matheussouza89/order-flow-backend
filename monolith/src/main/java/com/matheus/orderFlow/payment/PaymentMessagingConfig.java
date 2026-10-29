package com.matheus.orderFlow.payment;

import com.matheus.orderFlow.shared.messaging.RabbitMqConfig;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class PaymentMessagingConfig {

    static final String ORDER_CONFIRMED_QUEUE = "order.confirmed.payment";

    @Bean
    Queue orderConfirmedPaymentQueue() {
        return QueueBuilder.durable(ORDER_CONFIRMED_QUEUE).build();
    }

    @Bean
    Binding orderConfirmedPaymentBinding(Queue orderConfirmedPaymentQueue,
                                         TopicExchange orderEventsExchange) {
        return BindingBuilder
                .bind(orderConfirmedPaymentQueue)
                .to(orderEventsExchange)
                .with(RabbitMqConfig.ORDER_CONFIRMED_KEY);
    }
}
