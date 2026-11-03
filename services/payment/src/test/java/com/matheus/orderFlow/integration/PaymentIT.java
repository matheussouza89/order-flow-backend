package com.matheus.orderFlow.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.jayway.jsonpath.JsonPath;
import com.matheus.orderFlow.contracts.order.OrderConfirmedEvent;
import com.matheus.orderFlow.contracts.order.OrderFacts;
import com.matheus.orderFlow.shared.messaging.RabbitMqConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
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

    @Autowired
    private RabbitTemplate rabbitTemplate;

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

    private String confirmOrder() {
        UUID orderId = UUID.randomUUID();
        publishConfirmation(orderId);

        return orderId.toString();
    }

    private void publishConfirmation(UUID orderId) {
        rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE, OrderFacts.CONFIRMED_KEY, new OrderConfirmedEvent(
                orderId,
                customerId(),
                new BigDecimal("300.00"),
                List.of(new OrderConfirmedEvent.Item(UUID.randomUUID(), "Teclado", new BigDecimal("150.00"), 2,
                        new BigDecimal("300.00"))),
                1L,
                Instant.now()));
    }

    private void awaitPaymentStatus(String orderId, String expectedStatus) {
        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() ->
                        mockMvc.perform(get("/payments/orders/{orderId}", orderId)
                                        .header(HttpHeaders.AUTHORIZATION, asUser()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value(expectedStatus)));
    }

    private void stubApproval() {
        paymentGateway.stubFor(WireMock.post(urlPathEqualTo("/charges"))
                .willReturn(okJson("""
                        { "id": "ch_abc123", "status": "approved" }
                        """)));
    }

    @Test
    void shouldApprovePaymentWhenGatewayAccepts() throws Exception {
        stubApproval();

        String orderId = confirmOrder();

        awaitPaymentStatus(orderId, "APPROVED");

        mockMvc.perform(get("/payments/orders/{orderId}", orderId).header(HttpHeaders.AUTHORIZATION, asUser()))
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

        String orderId = confirmOrder();

        awaitPaymentStatus(orderId, "DECLINED");

        mockMvc.perform(get("/payments/orders/{orderId}", orderId).header(HttpHeaders.AUTHORIZATION, asUser()))
                .andExpect(jsonPath("$.reason").value("insufficient_funds"))
                .andExpect(jsonPath("$.gatewayReference").doesNotExist());
    }

    @Test
    void shouldFailPaymentWhenGatewayKeepsReturningServerError() throws Exception {
        paymentGateway.stubFor(WireMock.post(urlPathEqualTo("/charges"))
                .willReturn(aResponse().withStatus(500)));

        String orderId = confirmOrder();

        awaitPaymentStatus(orderId, "FAILED");

        mockMvc.perform(get("/payments/orders/{orderId}", orderId).header(HttpHeaders.AUTHORIZATION, asUser()))
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

        String orderId = confirmOrder();

        awaitPaymentStatus(orderId, "APPROVED");

        mockMvc.perform(get("/payments/orders/{orderId}", orderId).header(HttpHeaders.AUTHORIZATION, asUser()))
                .andExpect(jsonPath("$.gatewayReference").value("ch_recovered"));

        paymentGateway.verify(3, postRequestedFor(urlPathEqualTo("/charges")));
    }

    @Test
    void shouldStopCallingTheGatewayOnceTheCircuitBreakerOpens() throws Exception {
        paymentGateway.stubFor(WireMock.post(urlPathEqualTo("/charges"))
                .willReturn(aResponse().withStatus(500)));

        for (int i = 0; i < 3; i++) {
            awaitPaymentStatus(confirmOrder(), "FAILED");
        }

        CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker("paymentGateway");

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertEquals(CircuitBreaker.State.OPEN, breaker.getState()));

        paymentGateway.resetRequests();

        String orderId = confirmOrder();
        awaitPaymentStatus(orderId, "FAILED");

        paymentGateway.verify(0, postRequestedFor(urlPathEqualTo("/charges")));

        mockMvc.perform(get("/payments/orders/{orderId}", orderId).header(HttpHeaders.AUTHORIZATION, asUser()))
                .andExpect(jsonPath("$.reason").value(containsString("CircuitBreaker")));
    }

    @Test
    void shouldChargeOnceWhenTheSameConfirmationArrivesTwice() {
        paymentGateway.stubFor(WireMock.post(urlPathEqualTo("/charges"))
                .willReturn(okJson("""
                        { "id": "ch_{{request.headers.Idempotency-Key}}", "status": "approved" }
                        """).withTransformers("response-template")));
        UUID orderId = UUID.randomUUID();

        publishConfirmation(orderId);
        publishConfirmation(orderId);
        awaitPaymentStatus(confirmOrder(), "APPROVED");

        paymentGateway.verify(1, postRequestedFor(urlPathEqualTo("/charges"))
                .withHeader("Idempotency-Key", equalTo("order-" + orderId)));
    }

    @Test
    void shouldAnswerNotFoundToAnotherCustomer() throws Exception {
        stubApproval();
        String orderId = confirmOrder();
        awaitPaymentStatus(orderId, "APPROVED");

        String payment = mockMvc.perform(get("/payments/orders/{orderId}", orderId)
                        .header(HttpHeaders.AUTHORIZATION, asUser()))
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(get("/payments/{id}", JsonPath.<String>read(payment, "$.id"))
                        .header(HttpHeaders.AUTHORIZATION, asAnotherUser()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/payments/orders/{orderId}", orderId)
                        .header(HttpHeaders.AUTHORIZATION, asAnotherUser()))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/payments/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
