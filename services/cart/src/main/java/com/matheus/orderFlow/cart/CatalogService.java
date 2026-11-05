package com.matheus.orderFlow.cart;

import com.matheus.orderFlow.shared.exception.NotFoundException;
import com.matheus.orderFlow.shared.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
class CatalogService {
    private final CatalogClient catalogClient;

    @CircuitBreaker(name = ServiceClientsConfig.CATALOG, fallbackMethod = "unavailable")
    public CatalogProduct getProduct(UUID id) {
        try {
            return catalogClient.getProduct(id);
        } catch (HttpClientErrorException.NotFound exception) {
            throw new NotFoundException(id);
        }
    }

    CatalogProduct unavailable(UUID id, ResourceAccessException exception) {
        return refuse(id, exception);
    }

    CatalogProduct unavailable(UUID id, HttpServerErrorException exception) {
        return refuse(id, exception);
    }

    CatalogProduct unavailable(UUID id, CallNotPermittedException exception) {
        return refuse(id, exception);
    }

    private CatalogProduct refuse(UUID id, Exception exception) {
        log.warn("Catalog unavailable: productId={} cause={}", id, exception.toString());
        throw new ServiceUnavailableException(ServiceClientsConfig.CATALOG, exception);
    }
}
