package com.matheus.orderFlow.cart;

import com.matheus.orderFlow.shared.exception.DomainValidationException;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RedisHash("cart")
@Getter
class Cart {

    private static final long TTL_SECONDS = 7 * 24 * 60 * 60;

    @Id
    private String id;

    private Map<UUID, Integer> items;

    @TimeToLive
    private long ttlSeconds;

    protected Cart() {
    }

    Cart(String id) {
        this.id = id;
        this.items = new HashMap<>();
        resetTtl();
    }

    private void validateItem(UUID productId, int quantity) {
        if (productId == null) {
            throw new DomainValidationException("productId", "Product id cannot be null");
        }

        if (quantity <= 0) {
            throw new DomainValidationException("quantity", "Quantity must be greater than zero");
        }
    }

    private void resetTtl() {
        this.ttlSeconds = TTL_SECONDS;
    }

    void addItem(UUID productId, int quantity) {
        validateItem(productId, quantity);
        ensureItems();
        items.merge(productId, quantity, Integer::sum);
        resetTtl();
    }

    void setItemQuantity(UUID productId, int quantity) {
        if (productId == null) {
            throw new DomainValidationException("productId", "Product id cannot be null");
        }

        if (quantity < 0) {
            throw new DomainValidationException("quantity", "Quantity cannot be negative");
        }

        ensureItems();

        if (quantity == 0) {
            items.remove(productId);
        } else {
            items.put(productId, quantity);
        }

        resetTtl();
    }

    void removeItem(UUID productId) {
        setItemQuantity(productId, 0);
    }

    Map<UUID, Integer> getItems() {
        ensureItems();
        return items;
    }

    boolean isEmpty() {
        return getItems().isEmpty();
    }

    private void ensureItems() {
        if (items == null) {
            items = new HashMap<>();
        }
    }
}
