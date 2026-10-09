package com.matheus.orderFlow.shared.outbox;

import com.matheus.orderFlow.shared.messaging.RabbitMqConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private OutboxRelay outboxRelay;

    private static final String PAYLOAD = "{\"orderId\":\"abc\"}";

    private OutboxMessage pending() {
        return new OutboxMessage(RabbitMqConfig.ORDER_CONFIRMED_KEY,
                "com.matheus.orderFlow.order.OrderConfirmedEvent", PAYLOAD);
    }

    private void repositoryReturns(OutboxMessage... messages) {
        when(outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc())
                .thenReturn(List.of(messages));
    }

    @Test
    void shouldPublishAndMarkAsPublished() {
        OutboxMessage message = pending();
        repositoryReturns(message);

        outboxRelay.publishPending();

        verify(rabbitTemplate).send(eq(RabbitMqConfig.EXCHANGE),
                eq(RabbitMqConfig.ORDER_CONFIRMED_KEY), any(Message.class));
        assertTrue(message.isPublished());
    }

    @Test
    void shouldSendTheStoredPayloadAsJsonWithTheTypeHeader() {
        repositoryReturns(pending());

        outboxRelay.publishPending();

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(any(), any(), captor.capture());

        Message sent = captor.getValue();
        MessageProperties properties = sent.getMessageProperties();

        assertEquals(PAYLOAD, new String(sent.getBody(), StandardCharsets.UTF_8));
        assertEquals(MessageProperties.CONTENT_TYPE_JSON, properties.getContentType());
        assertEquals("com.matheus.orderFlow.order.OrderConfirmedEvent",
                properties.getHeader("__TypeId__"));
    }

    @Test
    void shouldKeepTheMessagePendingWhenTheBrokerFails() {
        OutboxMessage message = pending();
        repositoryReturns(message);
        doThrow(new AmqpException("broker unreachable"))
                .when(rabbitTemplate).send(any(), any(), any(Message.class));

        outboxRelay.publishPending();

        assertFalse(message.isPublished());
        assertEquals(1, message.getAttempts());
        assertEquals("broker unreachable", message.getLastError());
    }

    @Test
    void shouldKeepPublishingTheOthersWhenOneFails() {
        OutboxMessage failing = pending();
        OutboxMessage succeeding = pending();
        repositoryReturns(failing, succeeding);

        doThrow(new AmqpException("broker unreachable"))
                .doNothing()
                .when(rabbitTemplate).send(any(), any(), any(Message.class));

        outboxRelay.publishPending();

        assertFalse(failing.isPublished());
        assertTrue(succeeding.isPublished());
    }

    @Test
    void shouldDoNothingWhenThereIsNothingPending() {
        when(outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc())
                .thenReturn(List.of());

        outboxRelay.publishPending();

        verifyNoInteractions(rabbitTemplate);
    }
}
