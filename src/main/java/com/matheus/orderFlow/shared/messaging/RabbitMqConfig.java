package com.matheus.orderFlow.shared.messaging;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    public static final String EXCHANGE = "orderflow.events";
    public static final String ORDER_CONFIRMED_KEY = "order.confirmed";
    public static final String ORDER_SHIPPED_KEY = "order.shipped";
    public static final String ORDER_DELIVERED_KEY = "order.delivered";
    public static final String ORDER_CANCELLED_KEY = "order.cancelled";

    @Bean
    TopicExchange orderEventsExchange() {
        return new TopicExchange(EXCHANGE);
    }



    @Bean
    MessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
