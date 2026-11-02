package com.matheus.orderFlow.sales;

import java.util.UUID;

public record TopProductResponse(UUID productId, String productName, long quantity) {
}
