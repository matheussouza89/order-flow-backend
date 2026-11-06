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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UsersTableMoveIT {
    private static final String MOVE_SCRIPT = "../../docker/mysql/migrations/us6-users.sql";
    private static final String MONOLITH_USERS_TABLE = """
            CREATE TABLE orderflow.users
            (
                id            BINARY(16) PRIMARY KEY,
                name          VARCHAR(150) NOT NULL,
                email         VARCHAR(150) NOT NULL,
                password_hash VARCHAR(255) NOT NULL,
                role          VARCHAR(50)  NOT NULL,
                created_at    TIMESTAMP(6) NOT NULL,
                updated_at    TIMESTAMP(6) NOT NULL,
                CONSTRAINT uk_users_email UNIQUE (email)
            )""";
    private static final Map<UUID, String> USERS = Map.of(
            UUID.randomUUID(), "cliente@example.com",
            UUID.randomUUID(), "outro@example.com",
            UUID.randomUUID(), "admin@orderflow.local");

    private static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:latest"))
            .withCopyFileToContainer(MountableFile.forHostPath("../../docker/mysql/init/01-schemas.sql"),
                    "/docker-entrypoint-initdb.d/01-schemas.sql");

    static {
        MYSQL.start();
        asRoot(connection -> {
            execute(connection, MONOLITH_USERS_TABLE);
            USERS.forEach((id, email) -> execute(connection, """
                    INSERT INTO orderflow.users (id, name, email, password_hash, role, created_at, updated_at)
                    VALUES (UUID_TO_BIN('%s'), 'Pessoa', '%s', '$2a$04$hash', 'USER', NOW(6), NOW(6))"""
                    .formatted(id, email)));
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(MOVE_SCRIPT));
        });
    }

    @DynamicPropertySource
    static void connectAsTheIdentityService(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> jdbcUrl("identity"));
        registry.add("spring.datasource.username", () -> "identity");
        registry.add("spring.datasource.password", () -> "identity");
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

    private Map<UUID, String> usersInTheServiceSchema() {
        return new JdbcTemplate(dataSource).query("SELECT BIN_TO_UUID(id) AS id, email FROM users",
                        (row, index) -> Map.entry(UUID.fromString(row.getString("id")), row.getString("email")))
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Test
    void shouldMoveEveryUserToTheServiceSchemaWithoutCopies() {
        assertEquals(USERS, usersInTheServiceSchema());

        asRoot(connection -> {
            try (var tables = connection.getMetaData().getTables("orderflow", null, "users", null)) {
                assertThat(tables.next()).as("orderflow.users still exists").isFalse();
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
        assertEquals(USERS, usersInTheServiceSchema());
    }
}
