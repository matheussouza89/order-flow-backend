package com.matheus.orderFlow.shared.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.amqp.support.converter.MessageConverter;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FactTypeMappingTest {
    private static final String TYPE_ID_HEADER = "__TypeId__";
    private static final String SAMPLE_V1 = "sample.happened.v1";
    private static final String SAMPLE_LEGACY = "com.example.old.SampleHappened";

    record SampleHappened(UUID id, String name) {
    }

    private final MessageConverter converter = new RabbitMqConfig().messageConverter(
            new FactTypes(Map.of(SAMPLE_V1, SampleHappened.class),
                    Map.of(SAMPLE_LEGACY, SampleHappened.class)));

    private final UUID id = UUID.randomUUID();

    private Message json(String body, String typeId) {
        return MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setHeader(TYPE_ID_HEADER, typeId)
                .build();
    }

    @Test
    void shouldPublishTheLogicalTypeIdInsteadOfTheClassName() {
        Message message = converter.toMessage(new SampleHappened(id, "Teclado"), new MessageProperties());

        assertEquals(SAMPLE_V1, message.getMessageProperties().getHeader(TYPE_ID_HEADER));
    }

    @Test
    void shouldReadAMessagePublishedWithTheLegacyClassName() {
        Message message = json("""
                { "id": "%s", "name": "Teclado" }
                """.formatted(id), SAMPLE_LEGACY);

        assertEquals(new SampleHappened(id, "Teclado"), converter.fromMessage(message));
    }

    @Test
    void shouldIgnoreAFieldTheConsumerDoesNotKnowYet() {
        Message message = json("""
                { "id": "%s", "name": "Teclado", "addedLater": 42 }
                """.formatted(id), SAMPLE_V1);

        assertEquals(new SampleHappened(id, "Teclado"), converter.fromMessage(message));
    }

    @Test
    void shouldNotInstantiateATypeThatIsNotARegisteredFact() {
        Message message = json("{}", "java.lang.ProcessBuilder");

        assertThrows(MessageConversionException.class, () -> converter.fromMessage(message));
    }

    @Test
    void shouldRefuseToPublishAClassThatIsNotARegisteredFact() {
        assertThrows(MessageConversionException.class,
                () -> converter.toMessage("not a fact", new MessageProperties()));
    }
}
