package com.matheus.orderFlow.e2e;

import com.jayway.jsonpath.JsonPath;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TraceE2E extends AbstractE2E {

    private static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static HttpResponse<String> postTraced(String path, String bearer, String traceId) {
        return send(HttpRequest.newBuilder(gateway(path))
                .header("Authorization", bearer)
                .header("traceparent", "00-" + traceId + "-0000000000000001-01")
                .POST(HttpRequest.BodyPublishers.noBody()));
    }

    private static String spansOf(String traceId) {
        HttpResponse<String> trace = send(HttpRequest.newBuilder(URI.create("http://" + hostOf("zipkin", ZIPKIN_PORT)
                + ":" + portOf("zipkin", ZIPKIN_PORT) + "/api/v2/trace/" + traceId)).GET());
        assertEquals(200, trace.statusCode(), trace.body());

        return trace.body();
    }

    private static Set<String> servicesIn(String traceId) {
        List<String> services = JsonPath.read(spansOf(traceId), "$[*].localEndpoint.serviceName");
        return new HashSet<>(services);
    }

    private static boolean failedCallTo(String service, String traceId) {
        List<String> failedUrls = JsonPath.read(spansOf(traceId), "$[?(@.tags.error)].tags['http.url']");
        return failedUrls.stream().anyMatch(url -> url.startsWith("http://" + service + ":"));
    }

    private static void awaitTrace(String traceId, String description, Predicate<String> complete) {
        Awaitility.await(description)
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofSeconds(1))
                .ignoreExceptions()
                .until(() -> complete.test(traceId));
    }

    private static Customer customerWithItemInTheCart() {
        Customer customer = newCustomer();
        String productId = createProduct(adminBearer(), "Produto-" + UUID.randomUUID(), "15.00");

        Awaitility.await()
                .atMost(Duration.ofSeconds(90))
                .pollInterval(Duration.ofSeconds(2))
                .ignoreExceptions()
                .until(() -> post("/cart/items", customer.bearer(), """
                        { "productId": "%s", "quantity": 1 }
                        """.formatted(productId)).statusCode() == 200);

        return customer;
    }

    @Test
    void shouldFollowACheckoutThroughEveryServiceItCrosses() {
        Customer customer = customerWithItemInTheCart();
        String traceId = newTraceId();

        assertEquals(200, postTraced("/cart/checkout", customer.bearer(), traceId).statusCode());

        awaitTrace(traceId, "gateway, cart, orders and catalog in the checkout trace",
                id -> servicesIn(id).containsAll(Set.of("gateway", "cart", "orders", "catalog")));
    }

    @Test
    void shouldShowTheStepWhereTheCheckoutStoppedWhenTheCatalogIsDown() {
        Customer customer = customerWithItemInTheCart();
        String traceId = newTraceId();

        stop("catalog");
        try {
            assertThat(postTraced("/cart/checkout", customer.bearer(), traceId).statusCode()).isIn(503, 504);
        } finally {
            start("catalog");
        }

        awaitTrace(traceId, "the failed call to the catalog", id -> failedCallTo("catalog", id));
    }

    @Test
    void shouldShowTheStepWhereTheCheckoutStoppedWhenOrdersIsDown() {
        Customer customer = customerWithItemInTheCart();
        String traceId = newTraceId();

        stop("orders");
        try {
            assertThat(postTraced("/cart/checkout", customer.bearer(), traceId).statusCode()).isIn(503, 504);
        } finally {
            start("orders");
        }

        awaitTrace(traceId, "the failed call to orders", id -> failedCallTo("orders", id));
    }

    @Test
    void shouldCarryTheConfirmationTraceToTheConsumersOfTheFact() {
        Customer customer = newCustomer();
        String orderId = placeOrder(customer, createProduct(adminBearer(), "Produto-" + UUID.randomUUID(), "20.00"), 1);
        String traceId = newTraceId();

        assertEquals(200, postTraced("/orders/" + orderId + "/confirm", customer.bearer(), traceId).statusCode());

        awaitTrace(traceId, "payment and sales consuming the confirmation",
                id -> servicesIn(id).containsAll(Set.of("gateway", "orders", "payment", "sales")));
    }
}
