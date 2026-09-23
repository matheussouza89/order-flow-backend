package com.matheus.orderFlow.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        OrderStatus status,
        BigDecimal total,
        List<OrderItemResponse> items,
        Instant createdAt,
        Instant updatedAt
) {
    static OrderResponse from(Order order) {
        List<OrderItemResponse> itemsResponse = order.getItems().stream()
                .map(OrderItemResponse::from)
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getStatus(),
                order.getTotal(),
                itemsResponse,
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
