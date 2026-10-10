package com.matheus.orderFlow.user;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Access token issued on a successful login.")
public record TokenResponse(
        @Schema(description = "Signed JWT", example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ...")
        String token,

        @Schema(description = "Scheme to use in the Authorization header", example = "Bearer")
        String type,

        @Schema(description = "Seconds until the access token expires", example = "900")
        long expiresIn,

        @Schema(description = "Opaque token used to obtain a new access token",
                example = "0vJ8s2Qk...")
        String refreshToken
) {
    private static final String BEARER = "Bearer";

    static TokenResponse bearer(String token, long expiresIn, String refreshToken) {
        return new TokenResponse(token, BEARER, expiresIn, refreshToken);
    }
}
