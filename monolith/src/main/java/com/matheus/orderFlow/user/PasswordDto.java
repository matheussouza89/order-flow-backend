package com.matheus.orderFlow.user;

import io.swagger.v3.oas.annotations.media.Schema;

public record PasswordDto(
        @Schema(description = "New plain password, at least 8 characters",
                example = "umaNovaSenha1", minLength = 8)
        String password
) {
}
