package com.matheus.orderFlow.notification;

import com.matheus.orderFlow.shared.exception.DomainValidationException;
import com.matheus.orderFlow.shared.exception.InvalidStatusTransitionException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NotificationTest {
    private static final Instant NOW = Instant.parse("2026-11-16T10:00:00Z");
    private static final UUID ORDER = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();

    private Notification confirmation() {
        return new Notification(ORDER, OrderStatus.CONFIRMED, 1L, USER, new BigDecimal("150.00"), NOW);
    }

    @Test
    void shouldStartPendingAndDueRightAway() {
        Notification notification = confirmation();

        assertEquals(NotificationState.PENDING, notification.getState());
        assertEquals(0, notification.getAttempts());
        assertEquals(NOW, notification.getNextAttemptAt());
        assertNull(notification.getLastError());
    }

    @Test
    void shouldRequireTheOrderTheStatusAndTheVersion() {
        assertThrows(DomainValidationException.class,
                () -> new Notification(null, OrderStatus.SHIPPED, 2L, USER, null, NOW));
        assertThrows(DomainValidationException.class,
                () -> new Notification(ORDER, null, 2L, USER, null, NOW));
        assertThrows(DomainValidationException.class,
                () -> new Notification(ORDER, OrderStatus.SHIPPED, null, USER, null, NOW));
    }

    @Test
    void shouldRequireTheTotalOfAConfirmation() {
        assertThrows(DomainValidationException.class,
                () -> new Notification(ORDER, OrderStatus.CONFIRMED, 1L, USER, null, NOW));
    }

    @Test
    void shouldBeSent() {
        Notification notification = confirmation();

        notification.sent();

        assertEquals(NotificationState.SENT, notification.getState());
    }

    @Test
    void shouldWaitLongerAfterEachFailure() {
        Notification notification = confirmation();
        Instant moment = NOW;

        for (Duration wait : new Duration[]{
                Duration.ofSeconds(30), Duration.ofMinutes(1), Duration.ofMinutes(2), Duration.ofMinutes(4)}) {
            notification.failed("Connection refused", moment);

            assertEquals(NotificationState.PENDING, notification.getState());
            assertEquals(moment.plus(wait), notification.getNextAttemptAt());
            moment = notification.getNextAttemptAt();
        }

        assertEquals(4, notification.getAttempts());
        assertEquals("Connection refused", notification.getLastError());
    }

    @Test
    void shouldFailOnTheFifthFailure() {
        Notification notification = confirmation();

        for (int attempt = 1; attempt <= 5; attempt++) {
            notification.failed("Connection refused", NOW);
        }

        assertEquals(NotificationState.FAILED, notification.getState());
        assertEquals(5, notification.getAttempts());
    }

    @Test
    void shouldGiveUpWithoutRecipientOnTheFifthAttempt() {
        Notification notification = confirmation();

        for (int attempt = 1; attempt <= 4; attempt++) {
            notification.withoutRecipient(NOW);
            assertEquals(NotificationState.PENDING, notification.getState());
        }

        notification.withoutRecipient(NOW);

        assertEquals(NotificationState.NO_RECIPIENT, notification.getState());
        assertEquals(5, notification.getAttempts());
    }

    @Test
    void shouldKeepAtMost300CharactersOfTheError() {
        Notification notification = confirmation();

        notification.failed("x".repeat(500), NOW);

        assertEquals(300, notification.getLastError().length());
    }

    @Test
    void shouldBeSupersededByANewerStatusAlreadySent() {
        Notification notification = new Notification(ORDER, OrderStatus.SHIPPED, 2L, USER, null, NOW);

        notification.superseded();

        assertEquals(NotificationState.SUPERSEDED, notification.getState());
        assertThrows(InvalidStatusTransitionException.class, notification::sent);
    }

    @Test
    void shouldNotChangeAfterReachingAFinalState() {
        Notification notification = confirmation();
        notification.sent();

        assertThrows(InvalidStatusTransitionException.class, () -> notification.failed("late", NOW));
        assertThrows(InvalidStatusTransitionException.class, () -> notification.withoutRecipient(NOW));
        assertThrows(InvalidStatusTransitionException.class, notification::sent);

        assertEquals(NotificationState.SENT, notification.getState());
        assertEquals(0, notification.getAttempts());
        assertNull(notification.getLastError());
    }
}
