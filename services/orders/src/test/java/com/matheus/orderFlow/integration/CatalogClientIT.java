package com.matheus.orderFlow.integration;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Fault;
import com.jayway.jsonpath.JsonPath;
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

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
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
class CatalogClientIT extends AbstractIntegrationTest {
    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String TRACEPARENT = "00-" + TRACE_ID + "-00f067aa0ba902b7-01";
    private static final String EXPIRED = """
            {"status":401,"message":"Invalid or expired token","errors":{}}""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private CircuitBreaker breaker() {
        return circuitBreakerRegistry.circuitBreaker("catalog");
    }

    private ResultActions order(String productId, HttpHeaders headers) throws Exception {
        return mockMvc.perform(post("/orders")
                .headers(headers)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "items": [ { "productId": "%s", "quantity": 1 } ] }
                        """.formatted(productId)));
    }

    private ResultActions order(String productId) throws Exception {
        return order(productId, new HttpHeaders());
    }

    @Test
    void shouldForwardTheCallerCredentialToTheCatalog() throws Exception {
        String productId = catalogProduct("Teclado", "100.00");
        String caller = asUser();
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, caller);

        order(productId, headers).andExpect(status().isCreated());

        catalog().verify(getRequestedFor(urlEqualTo("/products/" + productId))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo(caller)));
    }

    @Test
    void shouldCallTheCatalogInsideTheTraceOfTheRequest() throws Exception {
        String productId = catalogProduct("Teclado", "100.00");
        HttpHeaders headers = new HttpHeaders();
        headers.set("traceparent", TRACEPARENT);

        order(productId, headers).andExpect(status().isCreated());

        catalog().verify(getRequestedFor(urlEqualTo("/products/" + productId))
                .withHeader("traceparent", matching("00-" + TRACE_ID + "-.*")));
    }

    @Test
    void shouldRefuseAnOrderForARemovedProductWithoutOpeningTheCircuit() throws Exception {
        String removedId = catalogProduct("Descontinuado", "70.00");
        removeFromCatalog(removedId);

        for (int attempt = 0; attempt < 6; attempt++) {
            order(removedId)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Could not find: " + removedId));
        }

        assertEquals(CircuitBreaker.State.CLOSED, breaker().getState());
        assertEquals(0, breaker().getMetrics().getNumberOfFailedCalls());
    }

    @Test
    void shouldPassAnyOtherRefusalOfTheCatalogThroughUnchanged() throws Exception {
        String productId = UUID.randomUUID().toString();
        catalog().stubFor(WireMock.get("/products/" + productId).willReturn(aResponse()
                .withStatus(401)
                .withHeader("Content-Type", "application/json")
                .withBody(EXPIRED)));

        order(productId)
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(EXPIRED));

        assertEquals(0, breaker().getMetrics().getNumberOfFailedCalls());
    }

    @Test
    void shouldAnswerServiceUnavailableInTimeWhenTheCatalogIsSlow() throws Exception {
        String productId = UUID.randomUUID().toString();
        catalog().stubFor(WireMock.get("/products/" + productId).willReturn(okJson("""
                { "id": "%s", "name": "Teclado", "price": 100.00 }
                """.formatted(productId)).withFixedDelay(2_000)));

        Instant start = Instant.now();
        order(productId)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value("Service unavailable: catalog"));
        assertThat(Duration.between(start, Instant.now())).isLessThan(Duration.ofSeconds(5));

        mockMvc.perform(get("/orders"))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void shouldStopCallingTheCatalogOnceTheCircuitOpens() throws Exception {
        String productId = UUID.randomUUID().toString();
        catalog().stubFor(WireMock.get("/products/" + productId).willReturn(aResponse().withStatus(500)));

        for (int attempt = 0; attempt < 5; attempt++) {
            order(productId).andExpect(status().isServiceUnavailable());
        }

        assertEquals(CircuitBreaker.State.OPEN, breaker().getState());
        catalog().resetRequests();

        order(productId)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Service unavailable: catalog"));
        catalog().verify(0, getRequestedFor(anyUrl()));
    }

    @Test
    void shouldListTheCallerOrdersWhileTheCatalogIsDown() throws Exception {
        String response = order(catalogProduct("Teclado", "100.00"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String orderId = JsonPath.read(response, "$.id");

        catalog().stubFor(WireMock.any(anyUrl()).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        mockMvc.perform(get("/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].productName").value("Teclado"));
    }
}
