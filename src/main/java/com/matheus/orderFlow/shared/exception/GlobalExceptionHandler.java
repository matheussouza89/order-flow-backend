package com.matheus.orderFlow.shared.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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

    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ErrorResponse> handleUnknownSortProperty(
            PropertyReferenceException exception
    ) {
        String message = "Cannot sort by unknown field '" + exception.getPropertyName() + "'";

        log.warn("Sort requested on unknown property: {}", exception.getPropertyName());

        Map<String, String> errors = new LinkedHashMap<>();
        errors.put("sort", message);

        ErrorResponse response = new ErrorResponse(400, "sort: " + message, errors);

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(HttpClientErrorException.Unauthorized.class)
    public ResponseEntity<ErrorResponse> handleUnauthorizedException(
            HttpClientErrorException.Unauthorized exception
    ) {
        ErrorResponse response = new ErrorResponse(401, exception.getMessage());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception) {

        String message = "Parameter '" + exception.getName() + "' has an invalid value";

        log.warn("Invalid value for parameter '{}'", exception.getName());

        Map<String, String> errors = new LinkedHashMap<>();
        errors.put(exception.getName(), message);

        ErrorResponse response = new ErrorResponse(
                400, exception.getName() + ": " + message, errors);

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException exception) {
        log.warn("Access denied: {}", exception.getMessage());

        ErrorResponse response = new ErrorResponse(
                403, "You do not have permission to perform this operation");

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
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

    @ExceptionHandler(UnavailableProductException.class)
    public ResponseEntity<ErrorResponse> handleUnavailableProduct(
            UnavailableProductException exception
    ) {
        log.warn("Checkout blocked by unavailable product: {}", exception.getMessage());

        ErrorResponse response = new ErrorResponse(409, exception.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleUserAlreadyExists(
            UserAlreadyExistsException exception
    ) {
        log.warn("User already exists: {}", exception.getMessage());

        ErrorResponse response = new ErrorResponse(409, exception.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(InvalidLoginException.class)
    public ResponseEntity<ErrorResponse> handleInvalidLogin(
            InvalidLoginException exception
    ) {
        log.warn("Invalid login attempt: {}", exception.getMessage());

        ErrorResponse response = new ErrorResponse(401, exception.getMessage());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }
}
