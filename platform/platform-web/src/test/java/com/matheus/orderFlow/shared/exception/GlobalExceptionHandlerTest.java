package com.matheus.orderFlow.shared.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldAnswerConflictWhenOrderWasModifiedConcurrently() {
        var response = handler.handleConcurrentModification(
                new OptimisticLockingFailureException("stale version"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void shouldAnswerServiceUnavailableInTheStandardFormat() {
        var response = handler.handleServiceUnavailable(
                new ServiceUnavailableException("orders", new ResourceAccessException("Connection refused")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(503);
        assertThat(response.getBody().message()).isEqualTo("Service unavailable: orders");
        assertThat(response.getBody().errors()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 404, 409})
    void shouldPassTheCalledServiceErrorThroughWithTheSameStatusAndBody(int status) {
        String body = """
                {"status":%d,"message":"from the called service","errors":{}}""".formatted(status);
        HttpClientErrorException fromCalledService = HttpClientErrorException.create(
                HttpStatus.valueOf(status), "", HttpHeaders.EMPTY,
                body.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);

        var response = handler.handleCalledServiceError(fromCalledService);

        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(new String(response.getBody(), StandardCharsets.UTF_8)).isEqualTo(body);
    }
}
