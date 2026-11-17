package com.matheus.orderFlow.contact;

import com.matheus.orderFlow.shared.exception.DomainValidationException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContactTest {
    private static final UUID USER = UUID.randomUUID();
    private static final Instant MOMENT = Instant.parse("2026-11-16T10:00:00Z");

    @Test
    void shouldRequireTheUserTheEmailAndTheMoment() {
        assertThrows(DomainValidationException.class, () -> new Contact(null, "ana@example.com", MOMENT));
        assertThrows(DomainValidationException.class, () -> new Contact(USER, " ", MOMENT));
        assertThrows(DomainValidationException.class, () -> new Contact(USER, null, MOMENT));
        assertThrows(DomainValidationException.class, () -> new Contact(USER, "ana@example.com", null));
    }

    @Test
    void shouldRefuseAnEmailLongerThan150Characters() {
        String tooLong = "a".repeat(139) + "@example.com";

        assertThrows(DomainValidationException.class, () -> new Contact(USER, tooLong, MOMENT));
    }

    @Test
    void shouldTakeTheEmailOfALaterChange() {
        Contact contact = new Contact(USER, "ana@example.com", MOMENT);

        assertTrue(contact.replaceIfNewer("ana.nova@example.com", MOMENT.plusSeconds(1)));
        assertEquals("ana.nova@example.com", contact.getEmail());
        assertEquals(MOMENT.plusSeconds(1), contact.getUpdatedAt());
    }

    @Test
    void shouldKeepTheEmailWhenTheChangeIsNotLater() {
        Contact contact = new Contact(USER, "ana.nova@example.com", MOMENT);

        assertFalse(contact.replaceIfNewer("ana@example.com", MOMENT.minusSeconds(1)));
        assertFalse(contact.replaceIfNewer("ana@example.com", MOMENT));
        assertEquals("ana.nova@example.com", contact.getEmail());
        assertEquals(MOMENT, contact.getUpdatedAt());
    }

    @Test
    void shouldNotChangeWhenTheLaterEmailIsInvalid() {
        Contact contact = new Contact(USER, "ana@example.com", MOMENT);

        assertThrows(DomainValidationException.class, () -> contact.replaceIfNewer(" ", MOMENT.plusSeconds(1)));
        assertEquals("ana@example.com", contact.getEmail());
        assertEquals(MOMENT, contact.getUpdatedAt());
    }
}
