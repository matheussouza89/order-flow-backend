package com.matheus.orderFlow.notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        UUID orderId,
        OrderStatus orderStatus,
        NotificationState state,
        int attempts,
        String lastError,
        Instant updatedAt
) {
    static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getOrderId(),
                notification.getOrderStatus(),
                notification.getState(),
                notification.getAttempts(),
                notification.getLastError(),
                notification.getUpdatedAt()
        );
    }
}
