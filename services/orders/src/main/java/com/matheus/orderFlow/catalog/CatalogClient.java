package com.matheus.orderFlow.catalog;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.UUID;

@HttpExchange("/products")
interface CatalogClient {

    @GetExchange("/{id}")
    CatalogProduct getProduct(@PathVariable UUID id);
}
