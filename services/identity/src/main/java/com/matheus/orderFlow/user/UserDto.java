package com.matheus.orderFlow.user;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Registration data. The role is assigned by the system.")
public record UserDto(
        @Schema(description = "Display name", example = "Matheus Souza", maxLength = 150)
        String name,

        @Schema(description = "Unique email address", example = "matheus@example.com", maxLength = 150)
        String email,

        @Schema(description = "Plain password, at least 8 characters. Stored only as a hash.",
                example = "umaSenhaSegura1", minLength = 8)
        String password
) {
}
