package com.matheus.orderFlow.shared.exception;


import java.util.UUID;

public class NotFoundException extends RuntimeException {
    public NotFoundException(UUID id) {
        super("Could not find: " + id);
    }
}
