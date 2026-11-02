package com.matheus.orderFlow.shared.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class BearerTokenPropagationInterceptorTest {

    private final BearerTokenPropagationInterceptor interceptor = new BearerTokenPropagationInterceptor();
    private final MockClientHttpRequest outgoing = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://orders/internal"));
    private final ClientHttpRequestExecution execution = (request, body) -> new MockClientHttpResponse(new byte[0], HttpStatus.OK);

    @AfterEach
    void clearTheContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldCarryTheCredentialOfTheCurrentCaller() throws IOException {
        Jwt jwt = Jwt.withTokenValue("caller-token")
                .header("alg", "RS256")
                .subject("caller")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        interceptor.intercept(outgoing, new byte[0], execution);

        assertThat(outgoing.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer caller-token");
    }

    @Test
    void shouldCarryNothingWithoutACaller() throws IOException {
        interceptor.intercept(outgoing, new byte[0], execution);

        assertThat(outgoing.getHeaders().containsHeader(HttpHeaders.AUTHORIZATION)).isFalse();
    }

    @Test
    void shouldCarryNothingForAnAnonymousCaller() throws IOException {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymous", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        interceptor.intercept(outgoing, new byte[0], execution);

        assertThat(outgoing.getHeaders().containsHeader(HttpHeaders.AUTHORIZATION)).isFalse();
    }
}
