package com.matheus.orderFlow.payment;

import com.matheus.orderFlow.shared.exception.DomainValidationException;
import com.matheus.orderFlow.shared.exception.InvalidStatusTransitionException;
import jakarta.persistence.*;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Entity
@Table(
        name = "payments"
)
@EntityListeners(AuditingEntityListener.class)
@Getter
class Payment {
    private static final int STATUS_MAX_LENGTH = 20;
    private static final int FAILURE_REASON_MAX_LENGTH = 300;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID orderId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = STATUS_MAX_LENGTH)
    private PaymentStatus status;

    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    @Column(unique = true)
    private String gatewayReference;

    @Column(length = FAILURE_REASON_MAX_LENGTH)
    private String reason;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    private void validate(
            UUID orderId,
            UUID userId,
            BigDecimal amount,
            String idempotencyKey
    ) {
        if (orderId == null) {
            throw new DomainValidationException("orderId", "Order ID cannot be null");
        }

        if (userId == null) {
            throw new DomainValidationException("userId", "Payment must belong to a user");
        }

        if (amount == null || amount.signum() <= 0) {
            throw new DomainValidationException("amount", "Amount must be greater than zero");
        }

        if (idempotencyKey == null) {
            throw new DomainValidationException("idempotencyKey", "Idempotency Key cannot be null");
        }
    }

    protected Payment() {
    }

    Payment(UUID orderId, UUID userId, BigDecimal amount, String idempotencyKey) {
        validate(orderId, userId, amount, idempotencyKey);

        this.orderId = orderId;
        this.userId = userId;
        this.amount = amount;
        this.status = PaymentStatus.PENDING;
        this.idempotencyKey = idempotencyKey;
    }

    boolean belongsTo(UUID candidate) {
        return userId.equals(candidate);
    }

    void approve(String gatewayReference) {
        requirePending(PaymentStatus.APPROVED);
        this.status = PaymentStatus.APPROVED;
        this.gatewayReference = gatewayReference;
    }

    void decline(String reason) {
        requirePending(PaymentStatus.DECLINED);
        this.status = PaymentStatus.DECLINED;
        this.reason = reason;
    }

    void fail(String reason) {
        requirePending(PaymentStatus.FAILED);
        this.status = PaymentStatus.FAILED;
        this.reason = reason;
    }

    private void requirePending(PaymentStatus expectedStatus) {
        if (this.status != PaymentStatus.PENDING) {
            throw new InvalidStatusTransitionException( "Only pending payments can be " + expectedStatus.name().toLowerCase());
        }
    }
}
