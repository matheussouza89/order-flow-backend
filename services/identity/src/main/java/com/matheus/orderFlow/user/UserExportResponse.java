package com.matheus.orderFlow.user;

import java.time.Instant;
import java.util.UUID;

public record UserExportResponse(
        UUID userId,
        String email,
        Instant updatedAt
) {
    static UserExportResponse from(User user) {
        return new UserExportResponse(user.getId(), user.getEmail(), user.getUpdatedAt());
    }
}
