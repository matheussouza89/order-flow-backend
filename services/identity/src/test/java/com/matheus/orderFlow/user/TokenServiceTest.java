package com.matheus.orderFlow.user;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TokenServiceTest {

    private static final Duration EXPIRATION = Duration.ofMinutes(15);

    private RSAKey key;
    private TokenService tokenService;
    private JwtDecoder jwtDecoder;

    private static RSAKey newKey() throws JOSEException {
        return new RSAKeyGenerator(2048).keyIDFromThumbprint(true).generate();
    }

    private static TokenService tokenServiceSigningWith(RSAKey key) {
        return new TokenService(new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key))), EXPIRATION);
    }

    @BeforeEach
    void setUp() throws JOSEException {
        key = newKey();
        tokenService = tokenServiceSigningWith(key);
        jwtDecoder = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build();
    }

    @Test
    void shouldSignWithRs256AndNameTheKey() {
        Jwt jwt = jwtDecoder.decode(tokenService.generateToken(UUID.randomUUID(), "USER"));

        assertEquals("RS256", jwt.getHeaders().get("alg").toString());
        assertEquals(key.getKeyID(), jwt.getHeaders().get("kid"));
    }

    @Test
    void shouldNameTheIssuer() {
        Jwt jwt = jwtDecoder.decode(tokenService.generateToken(UUID.randomUUID(), "USER"));

        assertEquals("orderflow", jwt.getClaimAsString("iss"));
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
    void shouldExpireFifteenMinutesAfterIssue() {
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
        Jwt jwt = jwtDecoder.decode(tokenService.generateToken(UUID.randomUUID(), "USER"));

        assertNull(jwt.getClaim("password"));
        assertNull(jwt.getClaim("email"));
        assertNull(jwt.getClaim("name"));
    }

    @Test
    void shouldRejectATokenSignedWithAnotherKey() throws JOSEException {
        String foreignToken = tokenServiceSigningWith(newKey())
                .generateToken(UUID.randomUUID(), "ADMIN");

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
