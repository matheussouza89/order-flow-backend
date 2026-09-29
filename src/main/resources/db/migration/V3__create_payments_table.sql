CREATE TABLE payments
(
    id                BINARY(16) PRIMARY KEY,
    order_id          BINARY(16)     NOT NULL,
    amount            DECIMAL(12, 2) NOT NULL,
    status            VARCHAR(20)    NOT NULL,
    idempotency_key   VARCHAR(255)   NOT NULL,
    gateway_reference VARCHAR(255),
    reason            VARCHAR(300),
    created_at        TIMESTAMP(6)   NOT NULL,
    updated_at        TIMESTAMP(6)   NOT NULL,
    CONSTRAINT uk_payments_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT uk_payments_gateway_reference UNIQUE (gateway_reference)
);

CREATE INDEX idx_payments_order_id ON payments (order_id);
