package com.matheus.orderFlow.integration;

import com.matheus.orderFlow.shared.messaging.RabbitMqConfig;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DeadLetterIT extends AbstractIntegrationTest {

    private static final String FAILING_QUEUE = "test.failing";

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AtomicInteger failedAttempts;

    @Test
    void shouldMoveAMessageToTheDeadLetterQueueAfterFiveFailedAttempts() {
        rabbitTemplate.send(RabbitMqConfig.EXCHANGE, FAILING_QUEUE,
                MessageBuilder.withBody("boom".getBytes(StandardCharsets.UTF_8))
                        .setContentType(MessageProperties.CONTENT_TYPE_TEXT_PLAIN)
                        .build());

        Message dead = Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .until(() -> rabbitTemplate.receive(RabbitMqConfig.DEAD_LETTER_QUEUE), Objects::nonNull);

        assertNotNull(dead);
        assertEquals(5, failedAttempts.get());
    }

    @TestConfiguration
    static class FailingConsumerConfig {

        @Bean
        AtomicInteger failedAttempts() {
            return new AtomicInteger();
        }

        @Bean
        Queue failingQueue() {
            return QueueBuilder.durable(FAILING_QUEUE)
                    .deadLetterExchange(RabbitMqConfig.DEAD_LETTER_EXCHANGE)
                    .build();
        }

        @Bean
        Binding failingBinding(Queue failingQueue, TopicExchange orderEventsExchange) {
            return BindingBuilder.bind(failingQueue).to(orderEventsExchange).with(FAILING_QUEUE);
        }

        @Bean
        FailingConsumer failingConsumer(AtomicInteger failedAttempts) {
            return new FailingConsumer(failedAttempts);
        }
    }

    static class FailingConsumer {

        private final AtomicInteger failedAttempts;

        FailingConsumer(AtomicInteger failedAttempts) {
            this.failedAttempts = failedAttempts;
        }

        @RabbitListener(queues = FAILING_QUEUE, containerFactory = RabbitMqConfig.RESILIENT_LISTENER_FACTORY)
        void onMessage(Message message) {
            failedAttempts.incrementAndGet();
            throw new IllegalStateException("always fails");
        }
    }
}
