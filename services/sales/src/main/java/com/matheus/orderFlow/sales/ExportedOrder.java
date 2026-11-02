package com.matheus.orderFlow.sales;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

record ExportedOrder(
        UUID orderId,
        UUID userId,
        String status,
        List<Item> items,
        long version,
        Instant confirmedAt,
        Instant updatedAt
) {
    record Item(UUID productId, String productName, BigDecimal unitPrice, int quantity, BigDecimal subtotal) {
    }
}
