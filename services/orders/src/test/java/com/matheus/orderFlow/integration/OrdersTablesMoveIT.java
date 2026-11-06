package com.matheus.orderFlow.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrdersTablesMoveIT {
    private static final String MOVE_SCRIPT = "../../docker/mysql/migrations/us7-orders.sql";
    private static final List<String> MONOLITH_TABLES = List.of("""
            CREATE TABLE orderflow.orders
            (
                id              BINARY(16) PRIMARY KEY,
                status          VARCHAR(20)    NOT NULL,
                total           DECIMAL(12, 2) NOT NULL,
                created_at      TIMESTAMP(6)   NOT NULL,
                updated_at      TIMESTAMP(6)   NOT NULL,
                user_id         BINARY(16)     NOT NULL,
                version         BIGINT         NOT NULL DEFAULT 0,
                idempotency_key VARCHAR(64)    NULL,
                INDEX idx_orders_user_id (user_id),
                CONSTRAINT uk_orders_user_idempotency_key UNIQUE (user_id, idempotency_key)
            )""", """
            CREATE TABLE orderflow.order_items
            (
                id           BINARY(16) PRIMARY KEY,
                order_id     BINARY(16)     NOT NULL,
                product_id   BINARY(16)     NOT NULL,
                product_name VARCHAR(150)   NOT NULL,
                unit_price   DECIMAL(12, 2) NOT NULL,
                quantity     INT            NOT NULL,
                subtotal     DECIMAL(12, 2) NOT NULL,
                CONSTRAINT fk_order_items_order
                    FOREIGN KEY (order_id) REFERENCES orderflow.orders (id) ON DELETE CASCADE
            )""", """
            CREATE TABLE orderflow.outbox_messages
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
            )""");
    private static final UUID FIRST_ORDER = UUID.randomUUID();
    private static final UUID SECOND_ORDER = UUID.randomUUID();
    private static final Map<UUID, UUID> ORDER_OF_ITEM = Map.of(
            UUID.randomUUID(), FIRST_ORDER,
            UUID.randomUUID(), FIRST_ORDER,
            UUID.randomUUID(), SECOND_ORDER);
    private static final Set<UUID> ORDERS = Set.copyOf(ORDER_OF_ITEM.values());
    private static final Set<UUID> MESSAGES = Set.of(UUID.randomUUID(), UUID.randomUUID());

    private static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:latest"))
            .withCopyFileToContainer(MountableFile.forHostPath("../../docker/mysql/init/01-schemas.sql"),
                    "/docker-entrypoint-initdb.d/01-schemas.sql");

    static {
        MYSQL.start();
        asRoot(connection -> {
            MONOLITH_TABLES.forEach(table -> execute(connection, table));
            ORDERS.forEach(order -> execute(connection, """
                    INSERT INTO orderflow.orders (id, status, total, created_at, updated_at, user_id, version, idempotency_key)
                    VALUES (UUID_TO_BIN('%s'), 'CONFIRMED', 20.00, NOW(6), NOW(6), UUID_TO_BIN(UUID()), 1, '%s')"""
                    .formatted(order, UUID.randomUUID())));
            ORDER_OF_ITEM.forEach((item, order) -> execute(connection, """
                    INSERT INTO orderflow.order_items (id, order_id, product_id, product_name, unit_price, quantity, subtotal)
                    VALUES (UUID_TO_BIN('%s'), UUID_TO_BIN('%s'), UUID_TO_BIN(UUID()), 'Produto', 10.00, 1, 10.00)"""
                    .formatted(item, order)));
            MESSAGES.forEach(message -> execute(connection, """
                    INSERT INTO orderflow.outbox_messages (id, routing_key, payload_type, payload, created_at)
                    VALUES (UUID_TO_BIN('%s'), 'order.confirmed', 'order.confirmed.v1', '{}', NOW(6))"""
                    .formatted(message)));
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(MOVE_SCRIPT));
        });
    }

    @DynamicPropertySource
    static void connectAsTheOrdersService(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> jdbcUrl("orders"));
        registry.add("spring.datasource.username", () -> "orders");
        registry.add("spring.datasource.password", () -> "orders");
    }

    @Autowired
    private DataSource dataSource;

    private static String jdbcUrl(String schema) {
        return "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(MySQLContainer.MYSQL_PORT) + "/" + schema;
    }

    private static void asRoot(Consumer<Connection> work) {
        try (Connection connection = DriverManager.getConnection(jdbcUrl("orderflow"), "root", MYSQL.getPassword())) {
            work.accept(connection);
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void execute(Connection connection, String sql) {
        try (var statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private Set<UUID> idsInTheServiceSchema(String table) {
        return new HashSet<>(new JdbcTemplate(dataSource).query("SELECT BIN_TO_UUID(id) FROM " + table,
                (row, index) -> UUID.fromString(row.getString(1))));
    }

    private Map<UUID, UUID> itemsInTheServiceSchema() {
        return new JdbcTemplate(dataSource).query("SELECT BIN_TO_UUID(id), BIN_TO_UUID(order_id) FROM order_items",
                        (row, index) -> Map.entry(UUID.fromString(row.getString(1)), UUID.fromString(row.getString(2))))
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Test
    void shouldMoveEveryOrderItemAndMessageToTheServiceSchemaWithoutCopies() {
        assertEquals(ORDERS, idsInTheServiceSchema("orders"));
        assertEquals(ORDER_OF_ITEM, itemsInTheServiceSchema());
        assertEquals(MESSAGES, idsInTheServiceSchema("outbox_messages"));
        assertEquals(List.of("orders.orders"), new JdbcTemplate(dataSource).queryForList("""
                SELECT CONCAT(UNIQUE_CONSTRAINT_SCHEMA, '.', REFERENCED_TABLE_NAME)
                FROM information_schema.REFERENTIAL_CONSTRAINTS
                WHERE CONSTRAINT_SCHEMA = 'orders' AND TABLE_NAME = 'order_items'""", String.class));

        asRoot(connection -> List.of("orders", "order_items", "outbox_messages").forEach(table -> {
            try (var tables = connection.getMetaData().getTables("orderflow", null, table, null)) {
                assertThat(tables.next()).as("orderflow." + table + " still exists").isFalse();
            } catch (SQLException exception) {
                throw new IllegalStateException(exception);
            }
        }));
    }

    @Test
    void shouldStartTheServiceOnTheMovedTablesWithoutTouchingThem() {
        List<Map<String, Object>> applied = new JdbcTemplate(dataSource).queryForList(
                "SELECT version, success FROM flyway_schema_history WHERE version = '1'");

        assertThat(applied).singleElement()
                .satisfies(migration -> assertThat(migration.get("success")).isEqualTo(true));
        assertEquals(ORDERS, idsInTheServiceSchema("orders"));
        assertEquals(ORDER_OF_ITEM, itemsInTheServiceSchema());
        assertEquals(MESSAGES, idsInTheServiceSchema("outbox_messages"));
    }
}
