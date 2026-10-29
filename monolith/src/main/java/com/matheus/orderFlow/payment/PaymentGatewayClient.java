package com.matheus.orderFlow.payment;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
class PaymentGatewayClient {

    private static final String GATEWAY = "paymentGateway";
    private static final String CURRENCY = "BRL";

    private final RestClient paymentGatewayRestClient;

    @Retry(name = GATEWAY)
    @CircuitBreaker(name = GATEWAY)
    ChargeResponse charge(String idempotencyKey, BigDecimal amount) {
        log.info("Charging gateway: reference={} amount={}", idempotencyKey, amount);

        return paymentGatewayRestClient.post()
                .uri("/charges")
                .header("Idempotency-Key", idempotencyKey)
                .body(new ChargeRequest(amount, CURRENCY, idempotencyKey))
                .retrieve()
                .body(ChargeResponse.class);
    }
}
