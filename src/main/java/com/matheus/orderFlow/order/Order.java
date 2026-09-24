package com.matheus.orderFlow.order;

import com.matheus.orderFlow.shared.exception.DomainValidationException;
import com.matheus.orderFlow.shared.exception.InvalidStatusTransitionException;
import jakarta.persistence.*;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
@EntityListeners(AuditingEntityListener.class)
@Getter
class Order {
    private static final int STATUS_MAX_LENGTH = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = STATUS_MAX_LENGTH)
    private OrderStatus status;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    protected Order() {
    }

    Order(List<OrderItem> items) {
        validate(items);

        this.status = OrderStatus.PENDING;
        items.forEach(this::addItem);
        recalculateTotal();
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    private void validate(List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            throw new DomainValidationException("items", "Order must have at least one item");
        }
    }

    private void addItem(OrderItem item) {
        items.add(item);
        item.assignTo(this);
    }

    private void recalculateTotal() {
        this.total = items.stream()
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    void confirm() {
        if (status != OrderStatus.PENDING) {
            throw new InvalidStatusTransitionException("Only pending orders can be confirmed");
        }
        this.status = OrderStatus.CONFIRMED;
    }

    void ship() {
        if (status != OrderStatus.CONFIRMED) {
            throw new InvalidStatusTransitionException("Only confirmed orders can be shipped");
        }
        this.status = OrderStatus.SHIPPED;
    }

    void deliver() {
        if (status != OrderStatus.SHIPPED) {
            throw new InvalidStatusTransitionException("Only shipped orders can be delivered");
        }
        this.status = OrderStatus.DELIVERED;
    }

    void cancel() {
        if (status != OrderStatus.PENDING && status != OrderStatus.CONFIRMED) {
            throw new InvalidStatusTransitionException("Only pending or confirmed orders can be cancelled");
        }
        this.status = OrderStatus.CANCELLED;
    }
}
