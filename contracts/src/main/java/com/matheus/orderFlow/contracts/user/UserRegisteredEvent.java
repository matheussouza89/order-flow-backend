package com.matheus.orderFlow.contracts.user;

import java.time.Instant;
import java.util.UUID;

public record UserRegisteredEvent(
        UUID userId,
        String email,
        Instant occurredAt
) {
}
