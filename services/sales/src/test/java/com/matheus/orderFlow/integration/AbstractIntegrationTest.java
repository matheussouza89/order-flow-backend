package com.matheus.orderFlow.integration;

import com.matheus.orderFlow.TestcontainersConfiguration;
import com.matheus.orderFlow.shared.test.TestJwtDecoderConfiguration;
import com.matheus.orderFlow.shared.test.TestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.UUID;

@Import({TestcontainersConfiguration.class, TestJwtDecoderConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    @Autowired
    private MongoTemplate mongoTemplate;

    protected String asUser() {
        return TestTokens.bearer(UUID.randomUUID(), "USER");
    }

    protected String asAdmin() {
        return TestTokens.bearer(UUID.randomUUID(), "ADMIN");
    }

    @BeforeEach
    void cleanDatabase() {
        mongoTemplate.remove(new Query(), "order_views");
    }
}
