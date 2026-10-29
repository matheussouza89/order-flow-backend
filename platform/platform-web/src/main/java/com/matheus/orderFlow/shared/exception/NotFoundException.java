package com.matheus.orderFlow.shared.exception;


import java.util.UUID;

public class NotFoundException extends RuntimeException {
    public NotFoundException(UUID id) {
        this(id.toString());
    }

    public NotFoundException(String id) {
        super("Could not find: " + id);
    }
}
