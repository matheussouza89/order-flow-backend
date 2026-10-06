package com.matheus.orderFlow.user;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Access token issued on a successful login.")
public record TokenResponse(
        @Schema(description = "Signed JWT", example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ...")
        String token,

        @Schema(description = "Scheme to use in the Authorization header", example = "Bearer")
        String type,

        @Schema(description = "Seconds until the token expires", example = "3600")
        long expiresIn
) {
    private static final String BEARER = "Bearer";

    static TokenResponse bearer(String token, long expiresIn) {
        return new TokenResponse(token, BEARER, expiresIn);
    }
}
