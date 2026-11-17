package com.matheus.orderFlow.contact;

import java.time.Instant;
import java.util.UUID;

record ExportedUser(
        UUID userId,
        String email,
        Instant updatedAt
) {
}
