package com.matheus.orderFlow.shared.exception;

public class InvalidStatusTransitionException extends ConflictException {
    public InvalidStatusTransitionException(String message) {
        super(message);
    }
}
