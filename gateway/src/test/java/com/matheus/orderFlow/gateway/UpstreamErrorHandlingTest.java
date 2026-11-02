package com.matheus.orderFlow.gateway;

import org.apache.hc.client5.http.ConnectTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class UpstreamErrorHandlingTest {

    private final UpstreamErrorHandling handling = new UpstreamErrorHandling();

    @Test
    void shouldAnswerServiceUnavailableWhenTheConnectionCannotBeOpenedInTime() {
        var response = handling.handleUnreachableService(new ResourceAccessException("I/O error",
                new ConnectTimeoutException("Connect to http://sales:8080 failed: Connect timed out")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().message()).isEqualTo("Service unavailable");
    }

    @Test
    void shouldAnswerGatewayTimeoutWhenTheServiceTakesTooLongToRespond() {
        var response = handling.handleUnreachableService(new ResourceAccessException("I/O error",
                new SocketTimeoutException("Read timed out")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(response.getBody().message()).isEqualTo("Service did not respond in time");
    }
}
