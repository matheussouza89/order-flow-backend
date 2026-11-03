package com.matheus.orderFlow.integration;

import com.matheus.orderFlow.TestcontainersConfiguration;
import com.matheus.orderFlow.shared.test.TestJwtDecoderConfiguration;
import com.matheus.orderFlow.shared.test.TestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

@Import({TestcontainersConfiguration.class, TestJwtDecoderConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final UUID customerId = UUID.randomUUID();

    protected UUID customerId() {
        return customerId;
    }

    protected String asUser() {
        return TestTokens.bearer(customerId, "USER");
    }

    protected String asAnotherUser() {
        return TestTokens.bearer(UUID.randomUUID(), "USER");
    }

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("DELETE FROM payments");
    }
}
