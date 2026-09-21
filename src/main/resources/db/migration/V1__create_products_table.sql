CREATE TABLE products
(
    id          BINARY(16) PRIMARY KEY,
    name        VARCHAR(150)   NOT NULL,
    description VARCHAR(1000),
    price       DECIMAL(12, 2) NOT NULL,
    created_at  TIMESTAMP(6)   NOT NULL,
    updated_at  TIMESTAMP(6)   NOT NULL
);