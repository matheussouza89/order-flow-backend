package com.matheus.orderFlow.shared.security;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;

import java.io.IOException;

public class BearerTokenPropagationInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof AbstractOAuth2TokenAuthenticationToken<?> caller) {
            request.getHeaders().setBearerAuth(caller.getToken().getTokenValue());
        }

        return execution.execute(request, body);
    }
}
