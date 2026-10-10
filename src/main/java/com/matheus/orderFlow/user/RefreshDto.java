package com.matheus.orderFlow.user;

import io.swagger.v3.oas.annotations.media.Schema;

public record RefreshDto(
        @Schema(description = "The refresh token returned by the previous login or refresh")
        String refreshToken
) {
}
