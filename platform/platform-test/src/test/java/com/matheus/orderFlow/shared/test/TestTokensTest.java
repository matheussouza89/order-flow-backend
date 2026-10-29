package com.matheus.orderFlow.shared.test;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestTokensTest {

    private final JwtDecoder decoder = new TestJwtDecoderConfiguration().jwtDecoder();

    @Test
    void shouldIssueTokensTheTestDecoderAccepts() {
        UUID userId = UUID.randomUUID();

        Jwt jwt = decoder.decode(TestTokens.token(userId, "ADMIN"));

        assertEquals("orderflow", jwt.getClaimAsString("iss"));
        assertEquals(userId.toString(), jwt.getSubject());
        assertEquals(List.of("ADMIN"), jwt.getClaimAsStringList("roles"));
        assertEquals("RS256", jwt.getHeaders().get("alg").toString());
    }

    @Test
    void shouldPrefixTheBearerScheme() {
        assertTrue(TestTokens.bearer(UUID.randomUUID(), "USER").startsWith("Bearer ey"));
    }
}
