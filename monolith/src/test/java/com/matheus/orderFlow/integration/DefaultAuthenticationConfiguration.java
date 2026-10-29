package com.matheus.orderFlow.integration;

import com.matheus.orderFlow.shared.security.TokenService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.UUID;

@TestConfiguration
public class DefaultAuthenticationConfiguration {

    static final UUID DEFAULT_USER_ID = UUID.randomUUID();

    @Bean
    public MockMvcBuilderCustomizer defaultAuthorizationHeader(TokenService tokenService) {
        String token = tokenService.generateToken(DEFAULT_USER_ID, "ADMIN");

        return builder -> builder.defaultRequest(
                MockMvcRequestBuilders.get("/")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }
}
