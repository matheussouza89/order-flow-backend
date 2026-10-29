package com.matheus.orderFlow.shared.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AuthenticatedUser {
    private static final String ADMIN_AUTHORITY = "ROLE_ADMIN";

    public UUID requireId() {
        Authentication authentication = currentAuthentication();

        if (authentication == null) {
            throw new IllegalStateException("No authenticated user in the current context");
        }

        return UUID.fromString(authentication.getName());
    }

    public boolean isAdmin() {
        Authentication authentication = currentAuthentication();

        if (authentication == null) {
            return false;
        }

        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ADMIN_AUTHORITY::equals);
    }

    private Authentication currentAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }

        return authentication;
    }
}
