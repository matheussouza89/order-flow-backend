package com.matheus.orderFlow.contracts.order;

import com.matheus.orderFlow.shared.messaging.FactTypes;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FactCompatibilityTest {
    private static final UUID ORDER = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final UUID PRODUCT = UUID.randomUUID();
    private static final Instant OCCURRED_AT = Instant.parse("2026-11-09T12:00:00Z");

    private final JacksonJsonMessageConverter consumer = new JacksonJsonMessageConverter();

    {
        consumer.setClassMapper(new FactTypes(OrderFacts.TYPES));
    }

    private static String statusChangeWith(String addedField) {
        return """
                { "orderId": "%s", "version": 3, "occurredAt": "%s", %s }
                """.formatted(ORDER, OCCURRED_AT, addedField);
    }

    static Stream<Arguments> factsWithInformationAddedAfterTheConsumerWasBuilt() {
        return Stream.of(
                Arguments.of(OrderFacts.CONFIRMED_V1, """
                                { "orderId": "%s", "userId": "%s", "total": 50.00, "couponCode": "NATAL10",
                                  "items": [ { "productId": "%s", "productName": "Teclado", "unitPrice": 25.00,
                                               "quantity": 2, "subtotal": 50.00, "weightKg": 1.2 } ],
                                  "version": 1, "occurredAt": "%s" }
                                """.formatted(ORDER, USER, PRODUCT, OCCURRED_AT),
                        new OrderConfirmedEvent(ORDER, USER, new BigDecimal("50.00"),
                                List.of(new OrderConfirmedEvent.Item(PRODUCT, "Teclado", new BigDecimal("25.00"), 2,
                                        new BigDecimal("50.00"))),
                                1L, OCCURRED_AT)),
                Arguments.of(OrderFacts.SHIPPED_V1, statusChangeWith("\"trackingCode\": \"BR123\""),
                        new OrderShippedEvent(ORDER, 3, OCCURRED_AT)),
                Arguments.of(OrderFacts.DELIVERED_V1, statusChangeWith("\"receivedBy\": \"Portaria\""),
                        new OrderDeliveredEvent(ORDER, 3, OCCURRED_AT)),
                Arguments.of(OrderFacts.CANCELLED_V1, statusChangeWith("\"reason\": { \"code\": \"CUSTOMER\" }"),
                        new OrderCancelledEvent(ORDER, 3, OCCURRED_AT)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("factsWithInformationAddedAfterTheConsumerWasBuilt")
    void shouldProcessAFactCarryingInformationTheConsumerDoesNotKnowYet(String typeId, String json, Object known) {
        Message message = MessageBuilder.withBody(json.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setHeader("__TypeId__", typeId)
                .build();

        assertEquals(known, consumer.fromMessage(message));
    }
}
