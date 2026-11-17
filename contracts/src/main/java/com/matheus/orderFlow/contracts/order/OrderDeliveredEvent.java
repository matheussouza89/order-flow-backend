package com.matheus.orderFlow.contracts.order;

import java.time.Instant;
import java.util.UUID;

public record OrderDeliveredEvent(
        UUID orderId,
        UUID userId,
        long version,
        Instant occurredAt
) {
}
