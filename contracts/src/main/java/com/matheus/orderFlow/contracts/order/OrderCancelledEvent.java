package com.matheus.orderFlow.contracts.order;

import java.time.Instant;
import java.util.UUID;

public record OrderCancelledEvent(
        UUID orderId,
        UUID userId,
        long version,
        Instant occurredAt
) {
}
