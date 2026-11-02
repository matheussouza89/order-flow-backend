package com.matheus.orderFlow.e2e;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SalesIsolationE2E extends AbstractE2E {

    private static final String TOP_PRODUCTS = "/admin/sales/top-products?limit=100";

    @Test
    void shouldConfirmOrdersWhileTheReportsAreDownAndCountThemOnceWhenTheyReturn() {
        String admin = adminBearer();
        String product = "Produto-" + UUID.randomUUID();
        String productId = createProduct(admin, product, "10.00");
        Customer customer = newCustomer();

        stop("sales");
        try {
            String orderId = order(customer, productId, 3);
            HttpResponse<String> confirmed = post("/orders/" + orderId + "/confirm", customer.bearer(), "");
            assertEquals(200, confirmed.statusCode(), confirmed.body());
            assertEquals(503, get(TOP_PRODUCTS, admin).statusCode());
        } finally {
            start("sales");
        }

        Awaitility.await()
                .atMost(Duration.ofSeconds(60))
                .until(() -> quantitySold(admin, product), quantity -> quantity == 3);
    }

    @Test
    void shouldRebuildTheSameReportsFromAnEmptyView() {
        String admin = adminBearer();
        String product = "Produto-" + UUID.randomUUID();
        String productId = createProduct(admin, product, "25.00");
        Customer customer = newCustomer();
        String orderId = order(customer, productId, 2);
        post("/orders/" + orderId + "/confirm", customer.bearer(), "");
        Awaitility.await()
                .atMost(Duration.ofSeconds(60))
                .until(() -> quantitySold(admin, product), quantity -> quantity == 2);
        String before = settledReports(admin);

        Container.ExecResult emptied = runIn("mongo", "mongosh", "--quiet", "orderflow",
                "--eval", "db.order_views.deleteMany({})");
        assertEquals(0, emptied.getExitCode(), emptied.getStderr());
        assertEquals(0L, quantitySold(admin, product));

        HttpResponse<String> rebuilt = post("/admin/sales/rebuild", admin, "");
        assertEquals(200, rebuilt.statusCode(), rebuilt.body());
        assertThat((Integer) read(rebuilt, "$.rebuilt")).isPositive();

        assertEquals(before, settledReports(admin));
    }

    private static String order(Customer customer, String productId, int quantity) {
        HttpResponse<String> created = post("/orders", customer.bearer(), """
                { "items": [ { "productId": "%s", "quantity": %d } ] }
                """.formatted(productId, quantity));
        assertEquals(201, created.statusCode(), created.body());

        return read(created, "$.id");
    }

    private static long quantitySold(String admin, String product) {
        HttpResponse<String> ranking = get(TOP_PRODUCTS, admin);
        assertEquals(200, ranking.statusCode(), ranking.body());

        List<Integer> quantities = read(ranking, "$[?(@.productName == '" + product + "')].quantity");
        return quantities.isEmpty() ? 0 : quantities.getFirst();
    }

    private static String settledReports(String admin) {
        String[] last = {null};

        Awaitility.await()
                .atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofSeconds(2))
                .until(() -> {
                    String current = reports(admin);
                    boolean unchanged = current.equals(last[0]);
                    last[0] = current;
                    return unchanged;
                });

        return last[0];
    }

    private static String reports(String admin) {
        String today = LocalDate.now().toString();

        return get(TOP_PRODUCTS, admin).body() + get("/admin/sales/daily?from=" + today + "&to=" + today, admin).body();
    }
}
