package com.matheus.orderFlow.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderConfirmedEvent(
        UUID orderId,
        BigDecimal total,
        Instant occurredAt
) {
}
