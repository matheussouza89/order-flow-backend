package com.matheus.orderFlow.order;

import java.util.UUID;

public record OrderItemDto(
    UUID productId,
    Integer quantity
) {
}
