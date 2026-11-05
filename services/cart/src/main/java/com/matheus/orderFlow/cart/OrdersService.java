package com.matheus.orderFlow.cart;

import com.matheus.orderFlow.shared.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import tools.jackson.databind.JsonNode;

@Slf4j
@Service
@RequiredArgsConstructor
class OrdersService {
    private final OrdersClient ordersClient;

    @CircuitBreaker(name = ServiceClientsConfig.ORDERS, fallbackMethod = "unavailable")
    public JsonNode createOrder(String idempotencyKey, OrderRequest order) {
        return ordersClient.createOrder(idempotencyKey, order);
    }

    JsonNode unavailable(String idempotencyKey, OrderRequest order, ResourceAccessException exception) {
        return refuse(idempotencyKey, exception);
    }

    JsonNode unavailable(String idempotencyKey, OrderRequest order, HttpServerErrorException exception) {
        return refuse(idempotencyKey, exception);
    }

    JsonNode unavailable(String idempotencyKey, OrderRequest order, CallNotPermittedException exception) {
        return refuse(idempotencyKey, exception);
    }

    private JsonNode refuse(String idempotencyKey, Exception exception) {
        log.warn("Orders unavailable: idempotencyKey={} cause={}", idempotencyKey, exception.toString());
        throw new ServiceUnavailableException(ServiceClientsConfig.ORDERS, exception);
    }
}
