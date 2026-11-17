package com.matheus.orderFlow.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
class NotificationMessages {
    private static final Locale BRAZIL = Locale.of("pt", "BR");

    private final String from;

    NotificationMessages(@Value("${orderflow.notifications.mail.from}") String from) {
        this.from = from;
    }

    SimpleMailMessage compose(Notification notification, String to) {
        String status = wordFor(notification.getOrderStatus());

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("Pedido %s %s".formatted(notification.getOrderId(), status));
        message.setText(textOf(notification, status));
        return message;
    }

    private static String textOf(Notification notification, String status) {
        String text = "Seu pedido %s foi %s.".formatted(notification.getOrderId(), status);

        if (notification.getOrderStatus() != OrderStatus.CONFIRMED) {
            return text;
        }

        return text + String.format(BRAZIL, " Total: R$ %,.2f.", notification.getTotal());
    }

    private static String wordFor(OrderStatus status) {
        return switch (status) {
            case CONFIRMED -> "confirmado";
            case SHIPPED -> "enviado";
            case DELIVERED -> "entregue";
            case CANCELLED -> "cancelado";
        };
    }
}
