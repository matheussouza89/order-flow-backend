package com.matheus.orderFlow.shared.exception;

public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String service, Throwable cause) {
        super("Service unavailable: " + service, cause);
    }
}
