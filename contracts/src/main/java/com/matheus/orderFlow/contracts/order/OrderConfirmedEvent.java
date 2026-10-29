package com.matheus.orderFlow.contracts.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderConfirmedEvent(
        UUID orderId,
        UUID userId,
        BigDecimal total,
        List<Item> items,
        Long version,
        Instant occurredAt
) {

    public record Item(
            UUID productId,
            String productName,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal subtotal
    ) {
    }
}
