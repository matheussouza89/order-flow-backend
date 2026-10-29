package com.matheus.orderFlow.user;

import java.util.UUID;

record RotatedToken(UUID userId, String refreshToken) {
}
