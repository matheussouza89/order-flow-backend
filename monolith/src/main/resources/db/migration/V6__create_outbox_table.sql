CREATE TABLE outbox_messages
(
    id           BINARY(16) PRIMARY KEY,
    routing_key  VARCHAR(100)  NOT NULL,
    payload_type VARCHAR(255)  NOT NULL,
    payload      TEXT          NOT NULL,
    created_at   TIMESTAMP(6)  NOT NULL,
    published_at TIMESTAMP(6)  NULL,
    attempts     INT           NOT NULL DEFAULT 0,
    last_error   VARCHAR(500)  NULL
);

CREATE INDEX idx_outbox_pending ON outbox_messages (published_at, created_at);
