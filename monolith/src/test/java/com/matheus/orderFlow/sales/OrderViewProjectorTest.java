package com.matheus.orderFlow.sales;

import com.matheus.orderFlow.contracts.order.OrderFacts;
import com.matheus.orderFlow.contracts.order.OrderConfirmedEvent;
import com.matheus.orderFlow.contracts.order.OrderShippedEvent;
import com.matheus.orderFlow.shared.messaging.RabbitMqConfig;
import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.ReplaceOptions;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderViewProjectorTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private OrderViewProjector projector;

    @Test
    void shouldDiscardConfirmationWithoutSnapshotWithoutTouchingMongo() {
        OrderConfirmedEvent withoutSnapshot = new OrderConfirmedEvent(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("100.00"), null, null, Instant.now());

        projector.on(withoutSnapshot);

        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void shouldLetMongoFailuresReachTheListenerSoTheMessageIsNotAcknowledged() {
        when(mongoTemplate.replace(any(Query.class), any(OrderView.class), any(ReplaceOptions.class), anyString()))
                .thenThrow(new DataAccessResourceFailureException("mongo unreachable"));

        OrderConfirmedEvent confirmed = new OrderConfirmedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("100.00"),
                List.of(new OrderConfirmedEvent.Item(UUID.randomUUID(), "Teclado", new BigDecimal("100.00"), 1, new BigDecimal("100.00"))),
                1L,
                Instant.now());

        assertThrows(DataAccessResourceFailureException.class, () -> projector.on(confirmed));
    }

    @Test
    void shouldDeferAStatusWhoseOrderIsNotProjectedYet() {
        OrderShippedEvent shipped = new OrderShippedEvent(UUID.randomUUID(), 2L, Instant.now());
        whenOrderIsNotProjected();

        projector.on(shipped, null);

        ArgumentCaptor<MessagePostProcessor> deferral = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(rabbitTemplate).convertAndSend(
                eq(SalesMessagingConfig.WAIT_EXCHANGE), eq(OrderFacts.SHIPPED_KEY), eq(shipped), deferral.capture());
        Message sent = deferral.getValue().postProcessMessage(new Message(new byte[0], new MessageProperties()));
        Object deferrals = sent.getMessageProperties().getHeader(SalesMessagingConfig.DEFERRALS_HEADER);
        assertEquals(1, deferrals);
    }

    @Test
    void shouldSendAStatusToTheDeadLetterQueueAfterTheLastDeferral() {
        OrderShippedEvent shipped = new OrderShippedEvent(UUID.randomUUID(), 2L, Instant.now());
        whenOrderIsNotProjected();

        projector.on(shipped, SalesMessagingConfig.MAX_DEFERRALS);

        verify(rabbitTemplate).convertAndSend(RabbitMqConfig.DEAD_LETTER_EXCHANGE, "", shipped);
        verify(rabbitTemplate, never()).convertAndSend(
                eq(SalesMessagingConfig.WAIT_EXCHANGE), anyString(), any(Object.class), any(MessagePostProcessor.class));
    }

    private void whenOrderIsNotProjected() {
        when(mongoTemplate.updateFirst(any(Query.class), any(UpdateDefinition.class), anyString()))
                .thenReturn(UpdateResult.acknowledged(0, 0L, null));
        when(mongoTemplate.findById(anyString(), eq(OrderView.class))).thenReturn(null);
    }
}
