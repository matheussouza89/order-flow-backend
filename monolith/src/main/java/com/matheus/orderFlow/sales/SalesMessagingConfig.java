package com.matheus.orderFlow.sales;

import com.matheus.orderFlow.contracts.order.OrderFacts;
import com.matheus.orderFlow.shared.messaging.RabbitMqConfig;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class SalesMessagingConfig {

    static final String PROJECTION_QUEUE = "order.views.projection";
    static final String WAIT_EXCHANGE = "orderflow.views.wait";
    static final String WAIT_QUEUE = "order.views.projection.wait";
    static final String DEFERRALS_HEADER = "projection-deferrals";
    static final int DEFERRAL_MILLIS = 10_000;
    static final int MAX_DEFERRALS = 9;

    @Bean
    Queue orderViewsProjectionQueue() {
        return QueueBuilder.durable(PROJECTION_QUEUE)
                .deadLetterExchange(RabbitMqConfig.DEAD_LETTER_EXCHANGE)
                .build();
    }

    @Bean
    Declarables orderViewsProjectionBindings(Queue orderViewsProjectionQueue, TopicExchange orderEventsExchange) {
        return new Declarables(
                bind(orderViewsProjectionQueue, orderEventsExchange, OrderFacts.CONFIRMED_KEY),
                bind(orderViewsProjectionQueue, orderEventsExchange, OrderFacts.SHIPPED_KEY),
                bind(orderViewsProjectionQueue, orderEventsExchange, OrderFacts.DELIVERED_KEY),
                bind(orderViewsProjectionQueue, orderEventsExchange, OrderFacts.CANCELLED_KEY)
        );
    }

    @Bean
    FanoutExchange orderViewsWaitExchange() {
        return new FanoutExchange(WAIT_EXCHANGE);
    }

    @Bean
    Queue orderViewsWaitQueue() {
        return QueueBuilder.durable(WAIT_QUEUE)
                .ttl(DEFERRAL_MILLIS)
                .deadLetterExchange(RabbitMqConfig.EXCHANGE)
                .build();
    }

    @Bean
    Binding orderViewsWaitBinding(Queue orderViewsWaitQueue, FanoutExchange orderViewsWaitExchange) {
        return BindingBuilder.bind(orderViewsWaitQueue).to(orderViewsWaitExchange);
    }

    private Binding bind(Queue queue, TopicExchange exchange, String routingKey) {
        return BindingBuilder.bind(queue).to(exchange).with(routingKey);
    }
}
