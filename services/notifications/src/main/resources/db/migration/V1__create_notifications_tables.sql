CREATE TABLE IF NOT EXISTS contacts
(
    user_id    BINARY(16) PRIMARY KEY,
    email      VARCHAR(150) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL
);

CREATE TABLE IF NOT EXISTS notifications
(
    id              BINARY(16) PRIMARY KEY,
    order_id        BINARY(16)     NOT NULL,
    order_status    VARCHAR(20)    NOT NULL,
    order_version   BIGINT         NOT NULL,
    user_id         BINARY(16)     NULL,
    total           DECIMAL(12, 2) NULL,
    state           VARCHAR(20)    NOT NULL,
    attempts        INT            NOT NULL,
    next_attempt_at TIMESTAMP(6)   NOT NULL,
    last_error      VARCHAR(300)   NULL,
    created_at      TIMESTAMP(6)   NOT NULL,
    updated_at      TIMESTAMP(6)   NOT NULL,
    CONSTRAINT uk_notifications_order_status UNIQUE (order_id, order_status),
    INDEX idx_notifications_due (state, next_attempt_at)
);
