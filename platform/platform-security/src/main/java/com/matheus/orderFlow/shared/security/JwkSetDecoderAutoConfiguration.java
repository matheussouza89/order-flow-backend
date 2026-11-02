package com.matheus.orderFlow.shared.security;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.net.URL;
import java.time.Duration;

@AutoConfiguration(beforeName = "org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration")
@ConditionalOnProperty(JwkSetDecoderAutoConfiguration.JWK_SET_URI)
public class JwkSetDecoderAutoConfiguration {
    static final String JWK_SET_URI = "spring.security.oauth2.resourceserver.jwt.jwk-set-uri";
    private static final long TOKEN_LIFETIME = Duration.ofMinutes(15).toMillis();

    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    JwtDecoder jwtDecoder(@Value("${" + JWK_SET_URI + "}") URL jwkSetUri) {
        return NimbusJwtDecoder.withJwkSource(publicKeys(jwkSetUri)).build();
    }

    static JWKSource<SecurityContext> publicKeys(URL jwkSetUri) {
        return JWKSourceBuilder.<SecurityContext>create(jwkSetUri)
                .cache(TOKEN_LIFETIME, JWKSourceBuilder.DEFAULT_CACHE_REFRESH_TIMEOUT)
                .outageTolerant(TOKEN_LIFETIME)
                .build();
    }
}
