package com.matheus.orderFlow.cart;

import java.util.UUID;

public record CartItemDto(
        UUID productId,
        int quantity
) {
}
