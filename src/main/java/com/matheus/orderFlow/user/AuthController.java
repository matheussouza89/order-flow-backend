package com.matheus.orderFlow.user;

import com.matheus.orderFlow.shared.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(
        name = "Authentication",
        description = "Registration and sign-in endpoints"
)
class AuthController {
    private final UserService userService;

    @Operation(summary = "Register",
            description = """
                    Creates an account. The role is assigned by the system, never sent by the
                    client, so registering never produces an administrator. The password is
                    stored only as a hash and is never returned.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account created",
                    headers = @Header(name = "Location",
                            description = "URI of the created user",
                            schema = @Schema(type = "string"))),
            @ApiResponse(responseCode = "400",
                    description = "Invalid name, email format or password shorter than 8 characters",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Email already registered",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/register")
    ResponseEntity<UserResponse> register(@RequestBody UserDto dto) {
        UserResponse created = userService.createUser(dto);

        URI location = ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .path("/users/{id}")
                .buildAndExpand(created.id())
                .toUri();

        return ResponseEntity.created(location).body(created);
    }
}
