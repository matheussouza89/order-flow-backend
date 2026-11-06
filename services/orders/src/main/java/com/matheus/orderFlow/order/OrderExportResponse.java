package com.matheus.orderFlow.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderExportResponse(
        UUID orderId,
        UUID userId,
        OrderStatus status,
        BigDecimal total,
        List<OrderItemResponse> items,
        long version,
        Instant confirmedAt,
        Instant updatedAt
) {
    static OrderExportResponse from(Order order, Instant confirmedAt) {
        return new OrderExportResponse(
                order.getId(),
                order.getUserId(),
                order.getStatus(),
                order.getTotal(),
                order.getItems().stream().map(OrderItemResponse::from).toList(),
                order.getVersion(),
                confirmedAt,
                order.getUpdatedAt()
        );
    }
}
