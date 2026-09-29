package com.matheus.orderFlow.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID orderId,
        BigDecimal amount,
        PaymentStatus status,
        String gatewayReference,
        String reason,
        Instant createdAt,
        Instant updatedAt
) {
    static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getGatewayReference(),
                payment.getReason(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}
