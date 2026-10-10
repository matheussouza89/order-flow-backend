package com.matheus.orderFlow.shared.security;

import java.util.UUID;

public record RotatedToken(UUID userId, String refreshToken) {
}
