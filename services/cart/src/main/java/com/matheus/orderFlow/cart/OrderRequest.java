package com.matheus.orderFlow.cart;

import java.util.List;
import java.util.Map;
import java.util.UUID;

record OrderRequest(List<Item> items) {

    record Item(UUID productId, int quantity) {
    }

    static OrderRequest of(Map<UUID, Integer> items) {
        return new OrderRequest(items.entrySet().stream()
                .map(entry -> new Item(entry.getKey(), entry.getValue()))
                .toList());
    }
}
