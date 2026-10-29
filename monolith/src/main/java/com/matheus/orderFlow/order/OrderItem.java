package com.matheus.orderFlow.order;

import com.matheus.orderFlow.shared.exception.DomainValidationException;
import jakarta.persistence.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(
        name = "order_items"
)
@Getter
class OrderItem {
    private static final int PRODUCT_NAME_MAX_LENGTH = 150;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false)
    private UUID productId;

    @Column(nullable = false, length = PRODUCT_NAME_MAX_LENGTH)
    private String productName;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    protected OrderItem() {
    }

    OrderItem(UUID productId, String productName, BigDecimal unitPrice, Integer quantity) {
        validate(productId, productName, unitPrice, quantity);

        this.productId = productId;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    private void validate(UUID productId, String productName, BigDecimal unitPrice, Integer quantity) {
        if (productId == null) {
            throw new DomainValidationException("productId", "Product id cannot be null");
        }

        if (productName == null || productName.isBlank()) {
            throw new DomainValidationException("productName", "Product name cannot be empty");
        }

        if (productName.length() > PRODUCT_NAME_MAX_LENGTH) {
            throw new DomainValidationException("productName",
                    "Product name cannot exceed " + PRODUCT_NAME_MAX_LENGTH + " characters");
        }

        if (unitPrice == null || unitPrice.signum() <= 0) {
            throw new DomainValidationException("unitPrice",
                    "Product price must be greater than zero");
        }

        if (quantity == null) {
            throw new DomainValidationException("quantity", "Quantity is required");
        }

        if (quantity <= 0) {
            throw new DomainValidationException("quantity", "Quantity must be greater than zero");
        }
    }

    void assignTo(Order order) {
        this.order = order;
    }
}
