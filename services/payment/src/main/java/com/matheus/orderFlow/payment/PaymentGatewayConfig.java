package com.matheus.orderFlow.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
class PaymentGatewayConfig {

    @Bean
    RestClient paymentGatewayRestClient(
            @Value("${orderflow.payment.gateway.url}") String baseUrl,
            @Value("${orderflow.payment.gateway.connect-timeout:2s}") Duration connectTimeout,
            @Value("${orderflow.payment.gateway.read-timeout:3s}") Duration readTimeout) {

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
