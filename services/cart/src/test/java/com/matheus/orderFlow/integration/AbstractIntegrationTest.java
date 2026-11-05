package com.matheus.orderFlow.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
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
import org.springframework.data.redis.connection.RedisConnectionFactory;
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
    private static final WireMockServer ORDERS = new WireMockServer(options().dynamicPort());

    static {
        CATALOG.start();
        ORDERS.start();
    }

    @DynamicPropertySource
    static void routeToTheFakeServices(DynamicPropertyRegistry registry) {
        registry.add("spring.http.serviceclient.catalog.base-url", CATALOG::baseUrl);
        registry.add("spring.http.serviceclient.orders.base-url", ORDERS::baseUrl);
    }

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    private final UUID customerId = UUID.randomUUID();

    protected String asUser() {
        return TestTokens.bearer(customerId, "USER");
    }

    protected String asAnotherUser() {
        return TestTokens.bearer(UUID.randomUUID(), "USER");
    }

    protected static WireMockServer catalog() {
        return CATALOG;
    }

    protected static WireMockServer orders() {
        return ORDERS;
    }

    protected String catalogProduct(String name, String price) {
        String id = UUID.randomUUID().toString();
        putInCatalog(id, name, price);

        return id;
    }

    protected void putInCatalog(String id, String name, String price) {
        putInCatalog(id, name, price, 0);
    }

    protected void putInCatalog(String id, String name, String price, int delayMillis) {
        CATALOG.stubFor(WireMock.get("/products/" + id).willReturn(okJson("""
                { "id": "%s", "name": "%s", "description": "Descricao", "price": %s }
                """.formatted(id, name, price)).withFixedDelay(delayMillis)));
    }

    protected void removeFromCatalog(String id) {
        CATALOG.stubFor(WireMock.get("/products/" + id).willReturn(aResponse()
                .withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"status":404,"message":"Resource not found: %s","errors":{}}""".formatted(id))));
    }

    protected String acceptOrders() {
        String id = UUID.randomUUID().toString();
        acceptOrders(id, 0);

        return id;
    }

    protected void acceptOrders(String id, int delayMillis) {
        answerOrders(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withHeader("Location", "/orders/" + id)
                .withBody("""
                        {"id":"%s","status":"PENDING","total":200.00,"items":[],
                         "createdAt":"2026-01-01T00:00:00Z","updatedAt":"2026-01-01T00:00:00Z"}
                        """.formatted(id))
                .withFixedDelay(delayMillis));
    }

    protected void answerOrders(ResponseDefinitionBuilder response) {
        ORDERS.stubFor(WireMock.post("/orders").willReturn(response));
    }

    @BeforeEach
    void cleanState() {
        redisConnectionFactory.getConnection().serverCommands().flushDb();

        CATALOG.resetAll();
        ORDERS.resetAll();
        circuitBreakerRegistry.circuitBreaker("catalog").reset();
        circuitBreakerRegistry.circuitBreaker("orders").reset();
    }
}
