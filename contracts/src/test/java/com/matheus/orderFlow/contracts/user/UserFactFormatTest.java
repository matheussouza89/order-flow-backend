package com.matheus.orderFlow.contracts.user;

import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class UserFactFormatTest {

    private static final Map<String, Class<?>> CONTACT = Map.of(
            "userId", UUID.class,
            "email", String.class,
            "occurredAt", Instant.class);

    private static Map<String, Class<?>> fieldsOf(Class<? extends Record> fact) {
        return Arrays.stream(fact.getRecordComponents())
                .collect(Collectors.toMap(RecordComponent::getName, RecordComponent::getType));
    }

    @Test
    void shouldKeepEveryFieldOfUserRegisteredV1() {
        assertThat(fieldsOf(UserRegisteredEvent.class)).containsAllEntriesOf(CONTACT);
    }

    @Test
    void shouldKeepEveryFieldOfUserEmailChangedV1() {
        assertThat(fieldsOf(UserEmailChangedEvent.class)).containsAllEntriesOf(CONTACT);
    }

    @Test
    void shouldPublishEachFactUnderItsVersionedId() {
        assertEquals(Map.of(
                "user.registered.v1", UserRegisteredEvent.class,
                "user.email-changed.v1", UserEmailChangedEvent.class), UserFacts.TYPES);
    }

    @Test
    void shouldRouteEachFactByItsOwnKey() {
        assertEquals("user.registered", UserFacts.REGISTERED_KEY);
        assertEquals("user.email-changed", UserFacts.EMAIL_CHANGED_KEY);
    }
}
