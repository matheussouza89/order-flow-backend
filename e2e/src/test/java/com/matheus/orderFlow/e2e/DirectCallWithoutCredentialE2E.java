package com.matheus.orderFlow.e2e;

import com.matheus.orderFlow.shared.test.TestTokens;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.http.HttpRequest;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DirectCallWithoutCredentialE2E extends AbstractE2E {

    @ParameterizedTest(name = "{0} {1} {2}")
    @CsvSource({
            "monolith, GET, /orders",
            "monolith, GET, /internal/orders/export",
            "sales, GET, /admin/sales/top-products",
            "payment, GET, /payments/orders/00000000-0000-0000-0000-000000000001",
            "catalog, POST, /products",
            "cart, GET, /cart"
    })
    void shouldRefuseADirectCallWithoutCredential(String service, String method, String protectedPath) {
        HttpRequest.Builder request = HttpRequest.newBuilder(service(service, protectedPath))
                .method(method, HttpRequest.BodyPublishers.noBody());

        assertEquals(401, send(request).statusCode());
    }

    @ParameterizedTest(name = "{0} {1} {2}")
    @CsvSource({
            "monolith, GET, /orders",
            "monolith, GET, /internal/orders/export",
            "sales, GET, /admin/sales/top-products",
            "payment, GET, /payments/orders/00000000-0000-0000-0000-000000000001",
            "catalog, POST, /products",
            "cart, GET, /cart"
    })
    void shouldRefuseADirectCallWithATokenSignedByAnotherKey(String service, String method, String protectedPath) {
        HttpRequest.Builder request = HttpRequest.newBuilder(service(service, protectedPath))
                .header("Authorization", TestTokens.bearer(UUID.randomUUID(), "ADMIN"))
                .method(method, HttpRequest.BodyPublishers.noBody());

        assertEquals(401, send(request).statusCode());
    }
}
