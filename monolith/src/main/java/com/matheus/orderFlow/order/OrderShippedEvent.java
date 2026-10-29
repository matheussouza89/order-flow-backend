package com.matheus.orderFlow.order;

import java.time.Instant;
import java.util.UUID;

public record OrderShippedEvent(
        UUID orderId,
        long version,
        Instant occurredAt
) {
}
