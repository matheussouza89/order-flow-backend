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
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PaymentsTableMoveIT {
    private static final String MOVE_SCRIPT = "../../docker/mysql/migrations/us3-payments.sql";
    private static final String MONOLITH_PAYMENTS_TABLE = """
            CREATE TABLE orderflow.payments
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
                user_id           BINARY(16)     NOT NULL,
                CONSTRAINT uk_payments_idempotency_key UNIQUE (idempotency_key),
                CONSTRAINT uk_payments_gateway_reference UNIQUE (gateway_reference)
            )""";
    private static final Map<UUID, BigDecimal> PAYMENTS = Map.of(
            UUID.randomUUID(), new BigDecimal("150.00"),
            UUID.randomUUID(), new BigDecimal("42.90"),
            UUID.randomUUID(), new BigDecimal("1000.00"));

    private static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:latest"))
            .withCopyFileToContainer(MountableFile.forHostPath("../../docker/mysql/init/01-schemas.sql"),
                    "/docker-entrypoint-initdb.d/01-schemas.sql");

    static {
        MYSQL.start();
        asRoot(connection -> {
            execute(connection, MONOLITH_PAYMENTS_TABLE);
            PAYMENTS.forEach((id, amount) -> execute(connection, """
                    INSERT INTO orderflow.payments
                        (id, order_id, user_id, amount, status, idempotency_key, created_at, updated_at)
                    VALUES (UUID_TO_BIN('%s'), UUID_TO_BIN(UUID()), UUID_TO_BIN(UUID()), %s, 'APPROVED',
                            'order-%s', NOW(6), NOW(6))""".formatted(id, amount, id)));
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(MOVE_SCRIPT));
        });
    }

    @DynamicPropertySource
    static void connectAsThePaymentService(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> jdbcUrl("payment"));
        registry.add("spring.datasource.username", () -> "payment");
        registry.add("spring.datasource.password", () -> "payment");
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

    private Map<UUID, BigDecimal> paymentsInTheServiceSchema() {
        return new JdbcTemplate(dataSource).query("SELECT BIN_TO_UUID(id) AS id, amount FROM payments",
                        (row, index) -> Map.entry(UUID.fromString(row.getString("id")), row.getBigDecimal("amount")))
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Test
    void shouldMoveEveryPaymentToTheServiceSchemaWithoutCopies() {
        assertEquals(PAYMENTS, paymentsInTheServiceSchema());

        asRoot(connection -> {
            try (var tables = connection.getMetaData().getTables("orderflow", null, "payments", null)) {
                assertThat(tables.next()).as("orderflow.payments still exists").isFalse();
            } catch (SQLException exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    @Test
    void shouldStartTheServiceOnTheMovedTableWithoutTouchingIt() {
        List<Map<String, Object>> applied = new JdbcTemplate(dataSource).queryForList(
                "SELECT version, success FROM flyway_schema_history WHERE version = '1'");

        assertThat(applied).singleElement()
                .satisfies(migration -> assertThat(migration.get("success")).isEqualTo(true));
        assertEquals(PAYMENTS, paymentsInTheServiceSchema());
    }
}
