package com.matheus.orderFlow.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties("orderflow.security")
public record PublicRoutes(List<String> publicPaths, List<String> publicGetPaths) {
    public PublicRoutes {
        publicPaths = publicPaths == null ? List.of() : List.copyOf(publicPaths);
        publicGetPaths = publicGetPaths == null ? List.of() : List.copyOf(publicGetPaths);
    }
}
