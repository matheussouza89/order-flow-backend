package com.matheus.orderFlow.shared.security;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.matheus.orderFlow.shared.test.TestTokens;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicKeyCacheTest {

    private static final String JWKS = "/.well-known/jwks.json";

    private final WireMockServer issuer = new WireMockServer(options().dynamicPort());

    @BeforeEach
    void publishTheIssuerKeys() throws Exception {
        issuer.start();
        String publicKeys = new JWKSet(new RSAKey.Builder(TestTokens.publicKey()).keyIDFromThumbprint().build())
                .toString();
        issuer.stubFor(get(urlEqualTo(JWKS)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody(publicKeys)));
    }

    @AfterEach
    void stopTheIssuer() {
        issuer.stop();
    }

    @Test
    void shouldKeepValidatingWithTheCachedKeysWhileTheIssuerIsDown() throws Exception {
        JwtDecoder decoder = decoder();
        decoder.decode(TestTokens.token(UUID.randomUUID(), "USER"));

        issuer.stop();
        var decoded = decoder.decode(TestTokens.token(UUID.randomUUID(), "ADMIN"));

        assertThat(decoded.getClaimAsStringList("roles")).containsExactly("ADMIN");
    }

    @Test
    void shouldFetchTheKeysOnlyOnceForManyTokens() throws Exception {
        JwtDecoder decoder = decoder();

        for (int i = 0; i < 5; i++) {
            decoder.decode(TestTokens.token(UUID.randomUUID(), "USER"));
        }

        issuer.verify(1, getRequestedFor(urlEqualTo(JWKS)));
    }

    @Test
    void shouldNotLoseTheCachedKeysWhenAnUnknownKeyShowsUpWhileTheIssuerIsDown() throws Exception {
        JwtDecoder decoder = decoder();
        decoder.decode(TestTokens.token(UUID.randomUUID(), "USER"));

        issuer.stop();
        String forged = tokenSignedByAnUnknownKey();
        assertThatThrownBy(() -> decoder.decode(forged)).isInstanceOf(JwtException.class);

        var decoded = decoder.decode(TestTokens.token(UUID.randomUUID(), "ADMIN"));
        assertThat(decoded.getClaimAsStringList("roles")).containsExactly("ADMIN");
    }

    private JwtDecoder decoder() throws Exception {
        return new JwkSetDecoderAutoConfiguration().jwtDecoder(URI.create(issuer.baseUrl() + JWKS).toURL());
    }

    private static String tokenSignedByAnUnknownKey() throws Exception {
        RSAKey unknown = new RSAKeyGenerator(2048)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .keyIDFromThumbprint(true)
                .generate();
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("orderflow")
                .subject(UUID.randomUUID().toString())
                .claim("roles", List.of("ADMIN"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(900))
                .build();

        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(unknown)))
                .encode(JwtEncoderParameters.from(
                        JwsHeader.with(SignatureAlgorithm.RS256).keyId(unknown.getKeyID()).build(), claims))
                .getTokenValue();
    }
}
