CREATE TABLE orders
(
    id         BINARY(16) PRIMARY KEY,
    status     VARCHAR(20)    NOT NULL,
    total      DECIMAL(12, 2) NOT NULL,
    created_at TIMESTAMP(6)   NOT NULL,
    updated_at TIMESTAMP(6)   NOT NULL
);

CREATE TABLE order_items
(
    id           BINARY(16) PRIMARY KEY,
    order_id     BINARY(16) NOT NULL,
    product_id   BINARY(16) NOT NULL,
    product_name VARCHAR(150)   NOT NULL,
    unit_price   DECIMAL(12, 2) NOT NULL,
    quantity     INT            NOT NULL,
    subtotal     DECIMAL(12, 2) NOT NULL,
    CONSTRAINT fk_order_items_order
        FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE
);