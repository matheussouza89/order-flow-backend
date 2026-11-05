package com.matheus.orderFlow.cart;

import com.matheus.orderFlow.shared.exception.ConflictException;

class UnavailableProductException extends ConflictException {
    UnavailableProductException(String message) {
        super(message);
    }
}
