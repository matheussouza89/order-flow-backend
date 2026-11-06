CREATE TABLE IF NOT EXISTS orders
(
    id              BINARY(16) PRIMARY KEY,
    user_id         BINARY(16)     NOT NULL,
    status          VARCHAR(20)    NOT NULL,
    total           DECIMAL(12, 2) NOT NULL,
    version         BIGINT         NOT NULL DEFAULT 0,
    idempotency_key VARCHAR(64)    NULL,
    created_at      TIMESTAMP(6)   NOT NULL,
    updated_at      TIMESTAMP(6)   NOT NULL,
    CONSTRAINT uk_orders_user_idempotency_key UNIQUE (user_id, idempotency_key),
    INDEX idx_orders_user_id (user_id)
);

CREATE TABLE IF NOT EXISTS order_items
(
    id           BINARY(16) PRIMARY KEY,
    order_id     BINARY(16)     NOT NULL,
    product_id   BINARY(16)     NOT NULL,
    product_name VARCHAR(150)   NOT NULL,
    unit_price   DECIMAL(12, 2) NOT NULL,
    quantity     INT            NOT NULL,
    subtotal     DECIMAL(12, 2) NOT NULL,
    CONSTRAINT fk_order_items_order
        FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS outbox_messages
(
    id           BINARY(16) PRIMARY KEY,
    routing_key  VARCHAR(100) NOT NULL,
    payload_type VARCHAR(255) NOT NULL,
    payload      TEXT         NOT NULL,
    created_at   TIMESTAMP(6) NOT NULL,
    published_at TIMESTAMP(6) NULL,
    attempts     INT          NOT NULL DEFAULT 0,
    last_error   VARCHAR(500) NULL,
    traceparent  VARCHAR(55)  NULL,
    INDEX idx_outbox_pending (published_at, created_at)
);
