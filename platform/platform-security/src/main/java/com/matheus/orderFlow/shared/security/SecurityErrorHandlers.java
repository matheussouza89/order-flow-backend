package com.matheus.orderFlow.shared.security;

import com.matheus.orderFlow.shared.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class SecurityErrorHandlers {
    private final ObjectMapper objectMapper;

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, exception) -> {
            log.debug("Unauthenticated request to {}", request.getRequestURI());

            write(response, HttpStatus.UNAUTHORIZED,
                    "Authentication is required to access this resource");
        };
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, exception) -> {
            log.warn("Access denied to {}", request.getRequestURI());

            write(response, HttpStatus.FORBIDDEN,
                    "You do not have permission to access this resource");
        };
    }

    private void write(HttpServletResponse response, HttpStatus status, String message)
            throws IOException {

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                objectMapper.writeValueAsString(new ErrorResponse(status.value(), message)));
    }
}
