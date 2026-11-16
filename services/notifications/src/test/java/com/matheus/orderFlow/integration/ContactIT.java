package com.matheus.orderFlow.integration;

import com.matheus.orderFlow.contact.ContactService;
import com.matheus.orderFlow.contracts.user.UserEmailChangedEvent;
import com.matheus.orderFlow.contracts.user.UserFacts;
import com.matheus.orderFlow.contracts.user.UserRegisteredEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ContactIT extends AbstractIntegrationTest {
    private static final Instant REGISTERED_AT = Instant.parse("2026-11-16T10:00:00Z");

    @Autowired
    private ContactService contactService;

    private final UUID userId = UUID.randomUUID();

    private void awaitEmail(String email) {
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertEquals(Optional.of(email), contactService.emailOf(userId)));
    }

    @Test
    void shouldKeepTheEmailOfARegisteredUser() {
        publish(UserFacts.REGISTERED_KEY, new UserRegisteredEvent(userId, "ana@example.com", REGISTERED_AT));

        awaitEmail("ana@example.com");
    }

    @Test
    void shouldFollowTheEmailChange() {
        publish(UserFacts.REGISTERED_KEY, new UserRegisteredEvent(userId, "ana@example.com", REGISTERED_AT));
        publish(UserFacts.EMAIL_CHANGED_KEY,
                new UserEmailChangedEvent(userId, "ana.nova@example.com", REGISTERED_AT.plusSeconds(60)));

        awaitEmail("ana.nova@example.com");
    }

    @Test
    void shouldNotGoBackToAnOlderEmailDeliveredLate() {
        publish(UserFacts.EMAIL_CHANGED_KEY,
                new UserEmailChangedEvent(userId, "ana.nova@example.com", REGISTERED_AT.plusSeconds(60)));
        awaitEmail("ana.nova@example.com");

        publish(UserFacts.REGISTERED_KEY, new UserRegisteredEvent(userId, "ana@example.com", REGISTERED_AT));
        UUID marker = UUID.randomUUID();
        publish(UserFacts.REGISTERED_KEY, new UserRegisteredEvent(marker, "marker@example.com", REGISTERED_AT));
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertEquals(Optional.of("marker@example.com"), contactService.emailOf(marker)));

        assertEquals(Optional.of("ana.nova@example.com"), contactService.emailOf(userId));
    }
}
