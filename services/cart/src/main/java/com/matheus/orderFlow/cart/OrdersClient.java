package com.matheus.orderFlow.cart;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import tools.jackson.databind.JsonNode;

@HttpExchange("/orders")
interface OrdersClient {

    @PostExchange
    JsonNode createOrder(@RequestHeader("Idempotency-Key") String idempotencyKey, @RequestBody OrderRequest order);
}
