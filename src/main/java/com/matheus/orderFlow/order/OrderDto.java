package com.matheus.orderFlow.order;

import java.util.List;

public record OrderDto(
    List<OrderItemDto> items
) {
}