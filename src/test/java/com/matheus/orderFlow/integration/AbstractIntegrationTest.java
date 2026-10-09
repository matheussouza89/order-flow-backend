package com.matheus.orderFlow.integration;

import com.matheus.orderFlow.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import com.matheus.orderFlow.shared.security.TokenService;

import java.util.UUID;

@Import({TestcontainersConfiguration.class, DefaultAuthenticationConfiguration.class})
@SpringBootTest(properties = "orderflow.security.bcrypt-strength=4")
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    @Autowired
    private TokenService tokenService;

    private final UUID customerId = UUID.randomUUID();
    private final UUID administratorId = UUID.randomUUID();

    protected UUID customerId() {
        return customerId;
    }

    protected String asUser() {
        return bearer(customerId, "USER");
    }

    protected String asAnotherUser() {
        return bearer(UUID.randomUUID(), "USER");
    }

    protected String asAdmin() {
        return bearer(administratorId, "ADMIN");
    }

    protected String bearer(UUID userId, String role) {
        return "Bearer " + tokenService.generateToken(userId, role);
    }

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("DELETE FROM outbox_messages");
        jdbcTemplate.execute("DELETE FROM payments");
        jdbcTemplate.execute("DELETE FROM order_items");
        jdbcTemplate.execute("DELETE FROM orders");
        jdbcTemplate.execute("DELETE FROM products");
        jdbcTemplate.execute("DELETE FROM users");

        redisConnectionFactory.getConnection().serverCommands().flushDb();
    }
}
