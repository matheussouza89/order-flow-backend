package com.matheus.orderFlow.user;

import com.matheus.orderFlow.shared.exception.UnauthorizedException;

public class InvalidLoginException extends UnauthorizedException {
    public InvalidLoginException(String message) {
        super(message);
    }
}
