package com.matheus.orderFlow.notification;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class NotificationMessagesTest {
    private static final UUID ORDER = UUID.fromString("7f0c1d2e-0000-4000-8000-000000000001");
    private static final Instant NOW = Instant.parse("2026-11-16T10:00:00Z");

    private final NotificationMessages messages = new NotificationMessages("nao-responda@orderflow.local");

    @Test
    void shouldTellTheNumberAndTheTotalOfAConfirmation() {
        Notification notification = new Notification(
                ORDER, OrderStatus.CONFIRMED, 1L, UUID.randomUUID(), new BigDecimal("1234.56"), NOW);

        SimpleMailMessage message = messages.compose(notification, "ana@example.com");

        assertEquals("nao-responda@orderflow.local", message.getFrom());
        assertArrayEquals(new String[]{"ana@example.com"}, message.getTo());
        assertEquals("Pedido " + ORDER + " confirmado", message.getSubject());
        assertEquals("Seu pedido " + ORDER + " foi confirmado. Total: R$ 1.234,56.", message.getText());
    }

    @ParameterizedTest
    @CsvSource({"SHIPPED, enviado", "DELIVERED, entregue", "CANCELLED, cancelado"})
    void shouldTellTheNumberAndTheNewStatus(OrderStatus status, String word) {
        Notification notification = new Notification(ORDER, status, 2L, UUID.randomUUID(), null, NOW);

        SimpleMailMessage message = messages.compose(notification, "ana@example.com");

        assertEquals("Pedido " + ORDER + " " + word, message.getSubject());
        assertEquals("Seu pedido " + ORDER + " foi " + word + ".", message.getText());
    }
}
