package com.matheus.orderFlow.shared.outbox;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OutboxMessageTest {

    private OutboxMessage message() {
        return new OutboxMessage("order.confirmed", "com.example.Event", "{}");
    }

    @Test
    void shouldStartUnpublishedWithoutAttempts() {
        OutboxMessage message = message();

        assertFalse(message.isPublished());
        assertEquals(0, message.getAttempts());
        assertNull(message.getLastError());
    }

    @Test
    void shouldMarkAsPublished() {
        OutboxMessage message = message();

        message.markPublished();

        assertTrue(message.isPublished());
        assertNotNull(message.getPublishedAt());
    }

    @Test
    void shouldCountAttemptsAndKeepTheLastError() {
        OutboxMessage message = message();

        message.markFailed("broker unreachable");
        message.markFailed("broker still unreachable");

        assertEquals(2, message.getAttempts());
        assertEquals("broker still unreachable", message.getLastError());
        assertFalse(message.isPublished());
    }

    @Test
    void shouldClearTheErrorOncePublished() {
        OutboxMessage message = message();

        message.markFailed("broker unreachable");
        message.markPublished();

        assertNull(message.getLastError());
        assertEquals(1, message.getAttempts());
    }

    @Test
    void shouldTruncateAnErrorLongerThanTheColumn() {
        OutboxMessage message = message();

        message.markFailed("a".repeat(900));

        assertEquals(500, message.getLastError().length());
    }

    @Test
    void shouldAcceptANullError() {
        OutboxMessage message = message();

        message.markFailed(null);

        assertNull(message.getLastError());
        assertEquals(1, message.getAttempts());
    }
}
