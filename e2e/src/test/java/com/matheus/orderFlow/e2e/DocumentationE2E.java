package com.matheus.orderFlow.e2e;

import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DocumentationE2E extends AbstractE2E {
    private static final List<String> PUBLIC_ROUTES = List.of(
            "/auth/register", "/auth/login", "/auth/refresh", "/auth/logout",
            "/users/{id}", "/users/{id}/name", "/users/{id}/email", "/users/{id}/password",
            "/products", "/products/{id}",
            "/cart", "/cart/items", "/cart/items/{productId}", "/cart/checkout",
            "/orders", "/orders/{id}", "/orders/{id}/confirm", "/orders/{id}/ship",
            "/orders/{id}/deliver", "/orders/{id}/cancel",
            "/payments/{id}", "/payments/orders/{orderId}",
            "/admin/sales/daily", "/admin/sales/top-products");

    private static List<Map<String, String>> serviceEntries() {
        HttpResponse<String> config = get("/v3/api-docs/swagger-config", null);
        assertEquals(200, config.statusCode(), config.body());

        return read(config, "$.urls");
    }

    @Test
    void shouldDocumentEveryPublicRouteOnTheSinglePage() {
        Set<String> documented = new HashSet<>();

        for (Map<String, String> entry : serviceEntries()) {
            HttpResponse<String> document = get(entry.get("url"), null);
            assertEquals(200, document.statusCode(), entry + " " + document.body());

            Map<String, Object> paths = read(document, "$.paths");
            documented.addAll(paths.keySet());
        }

        assertThat(documented).containsAll(PUBLIC_ROUTES);
        assertThat(documented).noneMatch(path -> path.startsWith("/internal") || path.startsWith("/.well-known"));
    }

    @Test
    void shouldPointTryItOutAtTheSingleEntry() {
        for (Map<String, String> entry : serviceEntries()) {
            HttpResponse<String> document = get(entry.get("url"), null);

            String server = read(document, "$.servers[0].url");

            assertEquals(gateway("").toString(), server);
        }
    }
}
