package com.matheus.orderFlow.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.jayway.jsonpath.JsonPath;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PaymentIT extends AbstractIntegrationTest {

    private static WireMockServer paymentGateway;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeAll
    static void startPaymentGateway() {
        paymentGateway = new WireMockServer(options().dynamicPort());
        paymentGateway.start();
    }

    @AfterAll
    static void stopPaymentGateway() {
        paymentGateway.stop();
    }

    @DynamicPropertySource
    static void paymentGatewayProperties(DynamicPropertyRegistry registry) {
        registry.add("orderflow.payment.gateway.url", paymentGateway::baseUrl);
    }

    @BeforeEach
    void resetGatewayAndBreaker() {
        paymentGateway.resetAll();
        circuitBreakerRegistry.circuitBreaker("paymentGateway").reset();
    }

    private String createConfirmedOrder() throws Exception {
        String productResponse = mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Teclado",
                                    "description": "Mecanico",
                                    "price": 150.00
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String productId = JsonPath.read(productResponse, "$.id");

        String orderResponse = mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "items": [
                                        { "productId": "%s", "quantity": 2 }
                                    ]
                                }
                                """.formatted(productId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String orderId = JsonPath.read(orderResponse, "$.id");

        mockMvc.perform(post("/orders/{id}/confirm", orderId))
                .andExpect(status().isOk());

        return orderId;
    }

    private void awaitPaymentStatus(String orderId, String expectedStatus) {
        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() ->
                        mockMvc.perform(get("/payments/orders/{orderId}", orderId))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value(expectedStatus)));
    }

    @Test
    void shouldApprovePaymentWhenGatewayAccepts() throws Exception {
        paymentGateway.stubFor(WireMock.post(urlPathEqualTo("/charges"))
                .willReturn(okJson("""
                        { "id": "ch_abc123", "status": "approved" }
                        """)));

        String orderId = createConfirmedOrder();

        awaitPaymentStatus(orderId, "APPROVED");

        mockMvc.perform(get("/payments/orders/{orderId}", orderId))
                .andExpect(jsonPath("$.gatewayReference").value("ch_abc123"))
                .andExpect(jsonPath("$.amount").value(300.00))
                .andExpect(jsonPath("$.reason").doesNotExist());

        paymentGateway.verify(postRequestedFor(urlPathEqualTo("/charges"))
                .withHeader("Idempotency-Key", matching("order-.*")));
    }

    @Test
    void shouldDeclinePaymentWhenGatewayRefuses() throws Exception {
        paymentGateway.stubFor(WireMock.post(urlPathEqualTo("/charges"))
                .willReturn(okJson("""
                        { "id": "ch_xyz789", "status": "declined", "reason": "insufficient_funds" }
                        """)));

        String orderId = createConfirmedOrder();

        awaitPaymentStatus(orderId, "DECLINED");

        mockMvc.perform(get("/payments/orders/{orderId}", orderId))
                .andExpect(jsonPath("$.reason").value("insufficient_funds"))
                .andExpect(jsonPath("$.gatewayReference").doesNotExist());
    }

    @Test
    void shouldFailPaymentWhenGatewayKeepsReturningServerError() throws Exception {
        paymentGateway.stubFor(WireMock.post(urlPathEqualTo("/charges"))
                .willReturn(aResponse().withStatus(500)));

        String orderId = createConfirmedOrder();

        awaitPaymentStatus(orderId, "FAILED");

        mockMvc.perform(get("/payments/orders/{orderId}", orderId))
                .andExpect(jsonPath("$.reason").isNotEmpty())
                .andExpect(jsonPath("$.gatewayReference").doesNotExist());

        paymentGateway.verify(3, postRequestedFor(urlPathEqualTo("/charges")));
    }

    @Test
    void shouldApprovePaymentWhenGatewayRecoversBeforeRetriesRunOut() throws Exception {
        String scenario = "flaky gateway";

        paymentGateway.stubFor(WireMock.post(urlPathEqualTo("/charges"))
                .inScenario(scenario)
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("first failure"));

        paymentGateway.stubFor(WireMock.post(urlPathEqualTo("/charges"))
                .inScenario(scenario)
                .whenScenarioStateIs("first failure")
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("second failure"));

        paymentGateway.stubFor(WireMock.post(urlPathEqualTo("/charges"))
                .inScenario(scenario)
                .whenScenarioStateIs("second failure")
                .willReturn(okJson("""
                        { "id": "ch_recovered", "status": "approved" }
                        """)));

        String orderId = createConfirmedOrder();

        awaitPaymentStatus(orderId, "APPROVED");

        mockMvc.perform(get("/payments/orders/{orderId}", orderId))
                .andExpect(jsonPath("$.gatewayReference").value("ch_recovered"));

        paymentGateway.verify(3, postRequestedFor(urlPathEqualTo("/charges")));
    }

    @Test
    void shouldStopCallingTheGatewayOnceTheCircuitBreakerOpens() throws Exception {
        paymentGateway.stubFor(WireMock.post(urlPathEqualTo("/charges"))
                .willReturn(aResponse().withStatus(500)));

        for (int i = 0; i < 3; i++) {
            awaitPaymentStatus(createConfirmedOrder(), "FAILED");
        }

        CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker("paymentGateway");

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertEquals(CircuitBreaker.State.OPEN, breaker.getState()));

        paymentGateway.resetRequests();

        String orderId = createConfirmedOrder();
        awaitPaymentStatus(orderId, "FAILED");

        paymentGateway.verify(0, postRequestedFor(urlPathEqualTo("/charges")));

        mockMvc.perform(get("/payments/orders/{orderId}", orderId))
                .andExpect(jsonPath("$.reason").value(containsString("CircuitBreaker")));
    }
}
