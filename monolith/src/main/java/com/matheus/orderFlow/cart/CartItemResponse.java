package com.matheus.orderFlow.cart;

import com.matheus.orderFlow.catalog.CatalogProduct;

import java.math.BigDecimal;
import java.util.UUID;

public record CartItemResponse(
        UUID productId,
        String productName,
        Integer quantity,
        BigDecimal price,
        BigDecimal totalPrice
) {
    static CartItemResponse of(CatalogProduct product, int quantity) {
        return new CartItemResponse(
                product.id(),
                product.name(),
                quantity,
                product.price(),
                product.price().multiply(BigDecimal.valueOf(quantity))
        );
    }
}
