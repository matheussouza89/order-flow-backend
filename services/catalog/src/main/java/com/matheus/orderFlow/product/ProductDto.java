package com.matheus.orderFlow.product;

import java.math.BigDecimal;

public record ProductDto(
        String name,
        String description,
        BigDecimal price
) {
}
