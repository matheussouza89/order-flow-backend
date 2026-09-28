package com.matheus.orderFlow.shared.messaging;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    public static final String EXCHANGE = "orderflow.events";
    public static final String ORDER_CONFIRMED_QUEUE = "order.confirmed.notification";
    public static final String ORDER_CONFIRMED_KEY = "order.confirmed";

    @Bean
    TopicExchange orderEventsExchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    Queue orderConfirmedQueue() {
        return QueueBuilder.durable(ORDER_CONFIRMED_QUEUE).build();
    }

    @Bean
    Binding orderConfirmedBinding(Queue orderConfirmedQueue, TopicExchange orderEventsExchange) {
        return BindingBuilder
                .bind(orderConfirmedQueue)
                .to(orderEventsExchange)
                .with(ORDER_CONFIRMED_KEY);
    }

    @Bean
    MessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
