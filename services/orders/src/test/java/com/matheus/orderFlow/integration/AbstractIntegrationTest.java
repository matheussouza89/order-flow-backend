package com.matheus.orderFlow.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.matheus.orderFlow.TestcontainersConfiguration;
import com.matheus.orderFlow.shared.test.TestJwtDecoderConfiguration;
import com.matheus.orderFlow.shared.test.TestTokens;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

@Import({TestcontainersConfiguration.class, TestJwtDecoderConfiguration.class, DefaultAuthenticationConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    private static final WireMockServer CATALOG = new WireMockServer(options().dynamicPort());

    static {
        CATALOG.start();
    }

    @DynamicPropertySource
    static void routeToTheFakeCatalog(DynamicPropertyRegistry registry) {
        registry.add("spring.http.serviceclient.catalog.base-url", CATALOG::baseUrl);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

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
        return TestTokens.bearer(userId, role);
    }

    protected static WireMockServer catalog() {
        return CATALOG;
    }

    protected String catalogProduct(String name, String price) {
        String id = UUID.randomUUID().toString();
        putInCatalog(id, name, price);

        return id;
    }

    protected void putInCatalog(String id, String name, String price) {
        CATALOG.stubFor(WireMock.get("/products/" + id).willReturn(okJson("""
                { "id": "%s", "name": "%s", "description": "Descricao", "price": %s }
                """.formatted(id, name, price))));
    }

    protected void removeFromCatalog(String id) {
        CATALOG.stubFor(WireMock.get("/products/" + id).willReturn(aResponse()
                .withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"status":404,"message":"Resource not found: %s","errors":{}}""".formatted(id))));
    }

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("DELETE FROM outbox_messages");
        jdbcTemplate.execute("DELETE FROM order_items");
        jdbcTemplate.execute("DELETE FROM orders");

        CATALOG.resetAll();
        circuitBreakerRegistry.circuitBreaker("catalog").reset();
    }
}
