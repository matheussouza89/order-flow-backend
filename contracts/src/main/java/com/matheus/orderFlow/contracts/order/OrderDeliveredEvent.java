package com.matheus.orderFlow.contracts.order;

import java.time.Instant;
import java.util.UUID;

public record OrderDeliveredEvent(
        UUID orderId,
        long version,
        Instant occurredAt
) {
}
