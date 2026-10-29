package com.matheus.orderFlow.user;

import io.swagger.v3.oas.annotations.media.Schema;

public record EmailDto(
        @Schema(description = "New email address", example = "novo@example.com", maxLength = 150)
        String email
) {
}
