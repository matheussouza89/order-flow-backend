package com.matheus.orderFlow.shared.exception;

public class UnavailableProductException extends RuntimeException {
    public UnavailableProductException(String message) {
        super(message);
    }
}
