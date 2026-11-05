package com.matheus.orderFlow.cart;

import java.math.BigDecimal;
import java.util.UUID;

record CatalogProduct(UUID id, String name, BigDecimal price) {
}
