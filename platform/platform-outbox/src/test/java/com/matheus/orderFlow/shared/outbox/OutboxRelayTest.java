package com.matheus.orderFlow.shared.outbox;

import com.matheus.orderFlow.shared.messaging.RabbitMqConfig;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

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

    @Mock
    private Tracer tracer;

    @Mock
    private Propagator propagator;

    @InjectMocks
    private OutboxRelay outboxRelay;

    private static final String ROUTING_KEY = "sample.happened";
    private static final String TYPE_ID = "sample.happened.v1";
    private static final String PAYLOAD = "{\"orderId\":\"abc\"}";
    private static final String TRACEPARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    private OutboxMessage pending() {
        return new OutboxMessage(ROUTING_KEY, TYPE_ID, PAYLOAD, null);
    }

    @Test
    void shouldPublishInsideTheTraceStoredWithTheMessage() {
        Span.Builder builder = mock(Span.Builder.class);
        Span span = mock(Span.class);
        Tracer.SpanInScope scope = mock(Tracer.SpanInScope.class);
        when(propagator.extract(eq(Map.of("traceparent", TRACEPARENT)), any())).thenReturn(builder);
        when(builder.name(any())).thenReturn(builder);
        when(builder.start()).thenReturn(span);
        when(tracer.withSpan(span)).thenReturn(scope);
        repositoryReturns(new OutboxMessage(ROUTING_KEY, TYPE_ID, PAYLOAD, TRACEPARENT));

        outboxRelay.publishPending();

        InOrder inOrder = inOrder(tracer, rabbitTemplate, scope, span);
        inOrder.verify(tracer).withSpan(span);
        inOrder.verify(rabbitTemplate).send(any(), any(), any(Message.class));
        inOrder.verify(scope).close();
        inOrder.verify(span).end();
    }

    @Test
    void shouldPublishAMessageWithoutTraceparentNormally() {
        OutboxMessage message = pending();
        repositoryReturns(message);

        outboxRelay.publishPending();

        verify(rabbitTemplate).send(any(), any(), any(Message.class));
        verifyNoInteractions(propagator);
        assertTrue(message.isPublished());
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
                eq(ROUTING_KEY), any(Message.class));
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
        assertEquals(TYPE_ID, properties.getHeader("__TypeId__"));
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
