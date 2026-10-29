package com.matheus.orderFlow.contracts.order;

import java.time.Instant;
import java.util.UUID;

public record OrderShippedEvent(
        UUID orderId,
        long version,
        Instant occurredAt
) {
}
