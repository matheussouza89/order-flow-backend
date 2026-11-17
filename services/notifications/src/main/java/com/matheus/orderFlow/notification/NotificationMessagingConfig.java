package com.matheus.orderFlow.notification;

import com.matheus.orderFlow.contracts.order.OrderFacts;
import com.matheus.orderFlow.shared.messaging.RabbitMqConfig;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class NotificationMessagingConfig {
    static final String ORDER_STATUS_QUEUE = "order.status.notifications";

    @Bean
    Queue orderStatusQueue() {
        return QueueBuilder.durable(ORDER_STATUS_QUEUE)
                .deadLetterExchange(RabbitMqConfig.DEAD_LETTER_EXCHANGE)
                .build();
    }

    @Bean
    Declarables orderStatusBindings(Queue orderStatusQueue, TopicExchange orderEventsExchange) {
        return new Declarables(
                BindingBuilder.bind(orderStatusQueue).to(orderEventsExchange).with(OrderFacts.CONFIRMED_KEY),
                BindingBuilder.bind(orderStatusQueue).to(orderEventsExchange).with(OrderFacts.SHIPPED_KEY),
                BindingBuilder.bind(orderStatusQueue).to(orderEventsExchange).with(OrderFacts.DELIVERED_KEY),
                BindingBuilder.bind(orderStatusQueue).to(orderEventsExchange).with(OrderFacts.CANCELLED_KEY)
        );
    }
}
