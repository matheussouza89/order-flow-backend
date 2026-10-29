package com.matheus.orderFlow.shared.exception;

import java.util.Map;

public record ErrorResponse(
    int status,
    String message,
    Map<String, String> errors
) {
    public ErrorResponse(int status, String message) {
        this(status, message, Map.of());
    }
}
