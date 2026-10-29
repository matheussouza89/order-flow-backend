package com.matheus.orderFlow.e2e;

import com.matheus.orderFlow.shared.test.TestTokens;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.http.HttpRequest;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DirectCallWithoutCredentialE2E extends AbstractE2E {

    @ParameterizedTest(name = "{0}{1}")
    @CsvSource({
            "monolith, /orders"
    })
    void shouldRefuseADirectCallWithoutCredential(String service, String protectedPath) {
        assertEquals(401, send(HttpRequest.newBuilder(service(service, protectedPath)).GET()).statusCode());
    }

    @ParameterizedTest(name = "{0}{1}")
    @CsvSource({
            "monolith, /orders"
    })
    void shouldRefuseADirectCallWithATokenSignedByAnotherKey(String service, String protectedPath) {
        HttpRequest.Builder request = HttpRequest.newBuilder(service(service, protectedPath))
                .header("Authorization", TestTokens.bearer(UUID.randomUUID(), "ADMIN"))
                .GET();

        assertEquals(401, send(request).statusCode());
    }
}
