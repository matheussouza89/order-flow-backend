package com.matheus.orderFlow.user;

import com.matheus.orderFlow.shared.exception.ConflictException;

public class UserAlreadyExistsException extends ConflictException {
    public UserAlreadyExistsException(String message) {
        super(message);
    }
}
