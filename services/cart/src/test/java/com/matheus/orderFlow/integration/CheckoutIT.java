package com.matheus.orderFlow.integration;

import com.github.tomakehurst.wiremock.http.Fault;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureTracing
@TestPropertySource(properties = "management.tracing.export.zipkin.enabled=false")
class CheckoutIT extends AbstractIntegrationTest {
    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String TRACEPARENT = "00-" + TRACE_ID + "-00f067aa0ba902b7-01";
    private static final String EXPIRED = """
            {"status":401,"message":"Invalid or expired token","errors":{}}""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private CircuitBreaker breaker() {
        return circuitBreakerRegistry.circuitBreaker("orders");
    }

    private void addItem(String productId, int quantity) throws Exception {
        mockMvc.perform(post("/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "productId": "%s", "quantity": %d }
                                """.formatted(productId, quantity)))
                .andExpect(status().isOk());
    }

    private ResultActions checkout() throws Exception {
        return mockMvc.perform(post("/cart/checkout"));
    }

    private void expectCartWith(int quantity) throws Exception {
        mockMvc.perform(get("/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(quantity));
    }

    private List<String> keysSentToOrders() {
        return orders().findAll(postRequestedFor(urlEqualTo("/orders"))).stream()
                .map(request -> request.getHeader("Idempotency-Key"))
                .toList();
    }

    @Test
    void shouldEmptyTheCartAndForgetTheKeyOnceTheOrderIsCreated() throws Exception {
        String productId = catalogProduct("Teclado", "100.00");
        addItem(productId, 2);
        String orderId = acceptOrders();

        checkout()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId))
                .andExpect(jsonPath("$.status").value("PENDING"));
        mockMvc.perform(get("/cart"))
                .andExpect(status().isNotFound());

        addItem(productId, 1);
        checkout().andExpect(status().isOk());

        List<String> keys = keysSentToOrders();
        assertThat(keys).hasSize(2);
        assertThat(Set.copyOf(keys)).hasSize(2);
    }

    @Test
    void shouldRetryWithTheSameKeyAfterTheOrderTakesLongerThanTheTimeout() throws Exception {
        addItem(catalogProduct("Teclado", "100.00"), 2);
        String orderId = UUID.randomUUID().toString();
        acceptOrders(orderId, 4_000);

        checkout()
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Service unavailable: orders"));
        expectCartWith(2);

        acceptOrders(orderId, 0);
        checkout()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId));

        List<String> keys = keysSentToOrders();
        assertThat(keys).hasSize(2);
        assertThat(Set.copyOf(keys)).hasSize(1);
    }

    @Test
    void shouldAnswerServiceUnavailableAndKeepTheCartWhenOrdersIsDown() throws Exception {
        addItem(catalogProduct("Teclado", "100.00"), 2);
        answerOrders(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER));

        checkout()
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value("Service unavailable: orders"));
        expectCartWith(2);
    }

    @Test
    void shouldPassAnExpiredCredentialThroughWithoutOpeningTheCircuit() throws Exception {
        addItem(catalogProduct("Teclado", "100.00"), 2);
        answerOrders(aResponse()
                .withStatus(401)
                .withHeader("Content-Type", "application/json")
                .withBody(EXPIRED));

        checkout()
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(EXPIRED));
        expectCartWith(2);

        assertEquals(CircuitBreaker.State.CLOSED, breaker().getState());
        assertEquals(0, breaker().getMetrics().getNumberOfFailedCalls());
    }

    @Test
    void shouldForwardTheCallerCredentialAndTheTraceToBothServices() throws Exception {
        String productId = catalogProduct("Teclado", "100.00");
        String caller = asUser();
        mockMvc.perform(post("/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, caller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "productId": "%s", "quantity": 1 }
                                """.formatted(productId)))
                .andExpect(status().isOk());
        acceptOrders();

        mockMvc.perform(post("/cart/checkout")
                        .header(HttpHeaders.AUTHORIZATION, caller)
                        .header("traceparent", TRACEPARENT))
                .andExpect(status().isOk());

        catalog().verify(getRequestedFor(urlEqualTo("/products/" + productId))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo(caller))
                .withHeader("traceparent", matching("00-" + TRACE_ID + "-.*")));
        orders().verify(postRequestedFor(urlEqualTo("/orders"))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo(caller))
                .withHeader("traceparent", matching("00-" + TRACE_ID + "-.*")));
    }

    @Test
    void shouldNotCallOrdersWhenTheDeadlinePassesWhileCheckingTheCatalog() throws Exception {
        List<String> productIds = new ArrayList<>();
        for (int item = 0; item < 4; item++) {
            String productId = catalogProduct("Item " + item, "10.00");
            addItem(productId, 1);
            productIds.add(productId);
        }
        productIds.forEach(id -> putInCatalog(id, "Item", "10.00", 1_200));
        acceptOrders();

        checkout()
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Service unavailable: catalog"));

        orders().verify(0, postRequestedFor(urlEqualTo("/orders")));
        productIds.forEach(id -> putInCatalog(id, "Item", "10.00"));
        mockMvc.perform(get("/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(4));
    }

    @Test
    void shouldKeepTheCartAndTheKeyWhenTheDeadlinePassesDuringACreationThatSucceeds() throws Exception {
        String productId = catalogProduct("Teclado", "100.00");
        addItem(productId, 2);
        putInCatalog(productId, "Teclado", "100.00", 1_200);
        String orderId = UUID.randomUUID().toString();
        acceptOrders(orderId, 3_000);

        checkout()
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Service unavailable: orders"));
        putInCatalog(productId, "Teclado", "100.00");
        expectCartWith(2);

        acceptOrders(orderId, 0);
        checkout()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId));
        mockMvc.perform(get("/cart"))
                .andExpect(status().isNotFound());

        List<String> keys = keysSentToOrders();
        assertThat(keys).hasSize(2);
        assertThat(Set.copyOf(keys)).hasSize(1);
    }

    @Test
    void shouldStopCallingOrdersOnceTheCircuitOpens() throws Exception {
        addItem(catalogProduct("Teclado", "100.00"), 1);
        answerOrders(aResponse().withStatus(500));

        for (int attempt = 0; attempt < 5; attempt++) {
            checkout().andExpect(status().isServiceUnavailable());
        }

        assertEquals(CircuitBreaker.State.OPEN, breaker().getState());
        orders().resetRequests();

        checkout()
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Service unavailable: orders"));
        orders().verify(0, postRequestedFor(urlEqualTo("/orders")));
        expectCartWith(1);
    }
}
