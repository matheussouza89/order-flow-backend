package com.matheus.orderFlow.shared.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_messages")
@EntityListeners(AuditingEntityListener.class)
@Getter
class OutboxMessage {
    private static final int LAST_ERROR_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "routing_key", nullable = false, length = 100, updatable = false)
    private String routingKey;

    @Column(name = "payload_type", nullable = false, updatable = false)
    private String payloadType;

    @Column(nullable = false, columnDefinition = "TEXT", updatable = false)
    private String payload;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = LAST_ERROR_MAX_LENGTH)
    private String lastError;

    protected OutboxMessage() {
    }

    OutboxMessage(String routingKey, String payloadType, String payload) {
        this.routingKey = routingKey;
        this.payloadType = payloadType;
        this.payload = payload;
        this.attempts = 0;
    }

    void markPublished() {
        this.publishedAt = Instant.now();
        this.lastError = null;
    }

    void markFailed(String error) {
        this.attempts++;
        this.lastError = error == null ? null : truncate(error);
    }

    boolean isPublished() {
        return publishedAt != null;
    }

    private String truncate(String error) {
        return error.length() <= LAST_ERROR_MAX_LENGTH
                ? error
                : error.substring(0, LAST_ERROR_MAX_LENGTH);
    }
}
