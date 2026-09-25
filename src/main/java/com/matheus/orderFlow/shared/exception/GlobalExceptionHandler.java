package com.matheus.orderFlow.shared.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFoundException(
            NotFoundException exception) {

        log.debug("Resource not found: {}", exception.getMessage());

        ErrorResponse response = new ErrorResponse(404, exception.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException exception) {

        Map<String, String> errors = new LinkedHashMap<>();

        exception.getBindingResult()
                .getFieldErrors()
                .forEach(error ->
                        errors.put(
                                error.getField(),
                                error.getDefaultMessage()
                        )
                );

        String message = errors.entrySet()
                .stream()
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .collect(Collectors.joining("; "));

        ErrorResponse response = new ErrorResponse(400, message, errors);

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleInvalidJson(
            HttpMessageNotReadableException exception) {

        ErrorResponse response = new ErrorResponse(400, "Invalid request body");

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(DomainValidationException.class)
    public ResponseEntity<ErrorResponse> handleDomainValidation(
            DomainValidationException exception
    ) {

        log.warn("Domain validation failed on field '{}': {}",
                exception.getField(), exception.getMessage());

        Map<String, String> errors = new LinkedHashMap<>();
        errors.put(exception.getField(), exception.getMessage());

        ErrorResponse response = new ErrorResponse(
                400,
                exception.getField() + ": " + exception.getMessage(),
                errors
        );

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(HttpClientErrorException.Unauthorized.class)
    public ResponseEntity<ErrorResponse> handleUnauthorizedException(
            HttpClientErrorException.Unauthorized exception
    ) {
        ErrorResponse response = new ErrorResponse(401, exception.getMessage());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception exception) {

        log.error("Unhandled exception", exception);

        ErrorResponse response = new ErrorResponse(500, "Internal Server Error");

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidStatusTransition(
            InvalidStatusTransitionException exception
    ) {
        log.warn("Invalid status transition: {}", exception.getMessage());

        ErrorResponse response = new ErrorResponse(409, exception.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }
}
