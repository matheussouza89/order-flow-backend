package com.matheus.orderFlow.notification;

import com.matheus.orderFlow.shared.exception.DomainValidationException;
import com.matheus.orderFlow.shared.exception.InvalidStatusTransitionException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@EntityListeners(AuditingEntityListener.class)
@Getter
class Notification {
    private static final int ENUM_MAX_LENGTH = 20;
    private static final int LAST_ERROR_MAX_LENGTH = 300;
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration FIRST_WAIT = Duration.ofSeconds(30);
    private static final String NO_RECIPIENT = "No email known for the customer";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = ENUM_MAX_LENGTH)
    private OrderStatus orderStatus;

    @Column(nullable = false, updatable = false)
    private Long orderVersion;

    @Column(updatable = false)
    private UUID userId;

    @Column(updatable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = ENUM_MAX_LENGTH)
    private NotificationState state;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private Instant nextAttemptAt;

    @Column(length = LAST_ERROR_MAX_LENGTH)
    private String lastError;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    protected Notification() {
    }

    Notification(UUID orderId, OrderStatus orderStatus, Long orderVersion, UUID userId, BigDecimal total,
                 Instant now) {
        if (orderId == null) {
            throw new DomainValidationException("orderId", "Order ID cannot be null");
        }

        if (orderStatus == null) {
            throw new DomainValidationException("orderStatus", "Order status cannot be null");
        }

        if (orderVersion == null) {
            throw new DomainValidationException("orderVersion", "Order version cannot be null");
        }

        if (orderStatus == OrderStatus.CONFIRMED && total == null) {
            throw new DomainValidationException("total", "A confirmation must tell the total");
        }

        this.orderId = orderId;
        this.orderStatus = orderStatus;
        this.orderVersion = orderVersion;
        this.userId = userId;
        this.total = total;
        this.state = NotificationState.PENDING;
        this.nextAttemptAt = now;
    }

    void sent() {
        requirePending();
        state = NotificationState.SENT;
    }

    void superseded() {
        requirePending();
        state = NotificationState.SUPERSEDED;
    }

    void failed(String error, Instant now) {
        requirePending();
        lastError = error.length() > LAST_ERROR_MAX_LENGTH ? error.substring(0, LAST_ERROR_MAX_LENGTH) : error;
        countAttempt(now, NotificationState.FAILED);
    }

    void withoutRecipient(Instant now) {
        requirePending();
        lastError = NO_RECIPIENT;
        countAttempt(now, NotificationState.NO_RECIPIENT);
    }

    private void countAttempt(Instant now, NotificationState finalState) {
        attempts++;

        if (attempts == MAX_ATTEMPTS) {
            state = finalState;
            return;
        }

        nextAttemptAt = now.plus(FIRST_WAIT.multipliedBy(1L << (attempts - 1)));
    }

    private void requirePending() {
        if (state != NotificationState.PENDING) {
            throw new InvalidStatusTransitionException("Only a pending notification can change; this one is " + state);
        }
    }
}
