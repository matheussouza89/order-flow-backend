package com.matheus.orderFlow.user;

import io.swagger.v3.oas.annotations.media.Schema;

public record NameDto(
        @Schema(description = "New display name", example = "Matheus Souza", maxLength = 150)
        String name
) {
}
