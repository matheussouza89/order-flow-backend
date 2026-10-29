package com.matheus.orderFlow.shared.messaging;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    public static final String EXCHANGE = "orderflow.events";
    public static final String DEAD_LETTER_EXCHANGE = "orderflow.events.dlx";
    public static final String DEAD_LETTER_QUEUE = "orderflow.events.dlq";
    public static final String RESILIENT_LISTENER_FACTORY = "resilientListenerContainerFactory";

    @Bean
    TopicExchange orderEventsExchange() {
        return new TopicExchange(EXCHANGE);
    }



    @Bean
    FanoutExchange deadLetterExchange() {
        return new FanoutExchange(DEAD_LETTER_EXCHANGE);
    }

    @Bean
    Queue deadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding deadLetterBinding(Queue deadLetterQueue, FanoutExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange);
    }

    @Bean
    SimpleRabbitListenerContainerFactory resilientListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .maxRetries(4)
                .backOffOptions(1_000, 2.0, 30_000)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build());
        return factory;
    }

    @Bean
    MessageConverter messageConverter(FactTypes factTypes) {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
        converter.setClassMapper(factTypes);
        return converter;
    }
}
