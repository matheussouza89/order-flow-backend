package com.matheus.orderFlow.order;

import java.time.Instant;
import java.util.UUID;

public record OrderCancelledEvent(
        UUID orderId,
        long version,
        Instant occurredAt
) {
}
