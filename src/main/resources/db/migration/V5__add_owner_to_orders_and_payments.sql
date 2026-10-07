ALTER TABLE orders
    ADD COLUMN user_id BINARY(16) NOT NULL;

ALTER TABLE payments
    ADD COLUMN user_id BINARY(16) NOT NULL;

CREATE INDEX idx_orders_user_id ON orders (user_id);
CREATE INDEX idx_payments_user_id ON payments (user_id);
