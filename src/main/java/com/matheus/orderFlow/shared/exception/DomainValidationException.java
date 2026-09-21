package com.matheus.orderFlow.shared.exception;

public class DomainValidationException extends RuntimeException {
    private final String field;

    public DomainValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
