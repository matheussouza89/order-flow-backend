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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
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

    @Operation(summary = "Login",
            description = """
                    Authenticates a user and returns an access token, to be sent in the
                    Authorization header of subsequent requests, and a refresh token, to be
                    exchanged for a new pair once the access token expires.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authentication successful"),
            @ApiResponse(responseCode = "401",
                    description = "Invalid credentials. The same response is returned whether the "
                            + "email is unknown or the password is wrong.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/login")
    TokenResponse login(@RequestBody LoginDto dto) {
        return userService.login(dto.email(), dto.password());
    }

    @Operation(summary = "Refresh",
            description = """
                    Exchanges a refresh token for a new access token. The refresh token is
                    rotated: the one sent stops working and a new one is returned. Reusing a
                    rotated token revokes every token of that login, since reuse suggests the
                    token was stolen.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "New token pair issued"),
            @ApiResponse(responseCode = "401",
                    description = "Refresh token unknown, expired, already used or revoked",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/refresh")
    TokenResponse refresh(@RequestBody RefreshDto dto) {
        return userService.refresh(dto.refreshToken());
    }

    @Operation(summary = "Logout",
            description = """
                    Revokes the refresh token and every other token issued from the same login,
                    so it cannot be exchanged again. The access token already issued stays valid
                    until it expires.
                    """)
    @ApiResponse(responseCode = "204", description = "Logged out")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping("/logout")
    void logout(@RequestBody RefreshDto dto) {
        userService.logout(dto.refreshToken());
    }
}
