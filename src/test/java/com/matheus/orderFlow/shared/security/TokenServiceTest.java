package com.matheus.orderFlow.shared.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TokenServiceTest {

    private static final String SECRET = "um-segredo-de-teste-com-mais-de-32-caracteres";
    private static final Duration EXPIRATION = Duration.ofHours(1);

    private TokenService tokenService;
    private JwtDecoder jwtDecoder;

    private SecretKey keyOf(String secret) {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    private TokenService tokenServiceWith(SecretKey key, Duration expiration) {
        return new TokenService(
                NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build(),
                expiration);
    }

    @BeforeEach
    void setUp() {
        SecretKey key = keyOf(SECRET);
        tokenService = tokenServiceWith(key, EXPIRATION);
        jwtDecoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Test
    void shouldCarryTheUserIdAsSubject() {
        UUID userId = UUID.randomUUID();

        Jwt jwt = jwtDecoder.decode(tokenService.generateToken(userId, "USER"));

        assertEquals(userId.toString(), jwt.getSubject());
    }

    @Test
    void shouldCarryTheRoleAsAList() {
        Jwt jwt = jwtDecoder.decode(tokenService.generateToken(UUID.randomUUID(), "ADMIN"));

        assertEquals(List.of("ADMIN"), jwt.getClaimAsStringList("roles"));
    }

    @Test
    void shouldExpireAfterTheConfiguredDuration() {
        Jwt jwt = jwtDecoder.decode(tokenService.generateToken(UUID.randomUUID(), "USER"));

        long seconds = Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).toSeconds();

        assertEquals(EXPIRATION.toSeconds(), seconds);
    }

    @Test
    void shouldProduceAThreePartToken() {
        String token = tokenService.generateToken(UUID.randomUUID(), "USER");

        assertEquals(3, token.split("\\.").length);
    }

    @Test
    void shouldNotCarryThePasswordOrTheEmail() {
        String token = tokenService.generateToken(UUID.randomUUID(), "USER");

        Jwt jwt = jwtDecoder.decode(token);

        assertNull(jwt.getClaim("password"));
        assertNull(jwt.getClaim("email"));
        assertNull(jwt.getClaim("name"));
    }

    @Test
    void shouldRejectATokenSignedWithAnotherSecret() {
        TokenService other = tokenServiceWith(
                keyOf("outro-segredo-completamente-diferente-123"), EXPIRATION);

        String foreignToken = other.generateToken(UUID.randomUUID(), "ADMIN");

        assertThrows(JwtException.class, () -> jwtDecoder.decode(foreignToken));
    }

    @Test
    void shouldRejectATamperedToken() {
        String token = tokenService.generateToken(UUID.randomUUID(), "USER");

        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1] + "." + parts[2].substring(1) + "X";

        assertThrows(JwtException.class, () -> jwtDecoder.decode(tampered));
    }

    @Test
    void shouldIssueTokensThatAreNotYetExpired() {
        Jwt jwt = jwtDecoder.decode(tokenService.generateToken(UUID.randomUUID(), "USER"));

        assertTrue(jwt.getExpiresAt().isAfter(Instant.now()));
    }

    @Test
    void shouldIssueTokensValidFromNow() {
        Instant before = Instant.now().minusSeconds(5);

        Jwt jwt = jwtDecoder.decode(tokenService.generateToken(UUID.randomUUID(), "USER"));

        assertTrue(jwt.getIssuedAt().isAfter(before));
    }
}
