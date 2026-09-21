package com.matheus.orderFlow.product;

import com.matheus.orderFlow.shared.exception.DomainValidationException;
import jakarta.persistence.*;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "products"
)
@EntityListeners(AuditingEntityListener.class)
@Getter
class Product {
    private static final int NAME_MAX_LENGTH = 150;
    private static final int DESCRIPTION_MAX_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    @Column(length = DESCRIPTION_MAX_LENGTH)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    public void validate(String name, String description, BigDecimal price) {
        if (name == null || name.isBlank()) {
            throw new DomainValidationException("name","Product name cannot be empty");
        }

        if (name.length() > NAME_MAX_LENGTH) {
            throw new DomainValidationException("name",
                    "Product name cannot exceed " + NAME_MAX_LENGTH + " characters");
        }

        if (description != null && description.length() > DESCRIPTION_MAX_LENGTH) {
            throw new DomainValidationException("description",
                    "Product description cannot exceed " + DESCRIPTION_MAX_LENGTH + " characters");
        }

        if (price == null || price.signum() <= 0) {
            throw new DomainValidationException("price","Product price cannot be negative");
        }

    }

    public Product() {
    }

    public Product(String name, String description, BigDecimal price) {
        validate(name, description, price);

        this.name = name;
        this.description = description;
        this.price = price;
    }

    public void update(String newName, String newDescription, BigDecimal newPrice) {
        validate(newName, newDescription, newPrice);

        this.name = newName;
        this.description = newDescription;
        this.price = newPrice;
    }
}
