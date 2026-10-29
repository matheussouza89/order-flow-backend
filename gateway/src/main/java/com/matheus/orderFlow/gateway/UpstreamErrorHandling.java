package com.matheus.orderFlow.gateway;

import com.matheus.orderFlow.shared.exception.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;

@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class UpstreamErrorHandling {

    @ExceptionHandler(ResourceAccessException.class)
    ResponseEntity<ErrorResponse> handleUnreachableService(ResourceAccessException exception) {
        Throwable cause = exception.getMostSpecificCause();

        if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException) {
            log.warn("Service did not respond in time: {}", exception.getMessage());

            return respond(HttpStatus.GATEWAY_TIMEOUT, "Service did not respond in time");
        }

        log.warn("Service unavailable: {}", exception.getMessage());

        return respond(HttpStatus.SERVICE_UNAVAILABLE, "Service unavailable");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErrorResponse> handleUnroutedPath(NoResourceFoundException exception) {
        return respond(HttpStatus.NOT_FOUND, "Could not find: /" + exception.getResourcePath());
    }

    private ResponseEntity<ErrorResponse> respond(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(status.value(), message));
    }
}
