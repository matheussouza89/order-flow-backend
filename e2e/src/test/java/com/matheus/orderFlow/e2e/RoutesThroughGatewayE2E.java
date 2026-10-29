package com.matheus.orderFlow.e2e;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RoutesThroughGatewayE2E extends AbstractE2E {

    private static void assertError(HttpResponse<String> response, int status) {
        assertEquals(status, response.statusCode(), response.body());
        assertEquals(status, (int) read(response, "$.status"));
        assertThat((String) read(response, "$.message")).isNotBlank();
        assertThat((Map<?, ?>) read(response, "$.errors")).isNotNull();
    }

    private static void assertPage(HttpResponse<String> response) {
        assertEquals(200, response.statusCode(), response.body());
        assertThat((List<?>) read(response, "$.content")).isNotNull();
        assertThat(response.body()).contains("\"page\"", "\"size\"", "\"totalElements\"", "\"totalPages\"");
    }

    @Test
    void shouldServeTheAccountRoutes() {
        Customer customer = newCustomer();

        HttpResponse<String> user = get("/users/" + customer.id(), customer.bearer());
        assertEquals(200, user.statusCode(), user.body());
        assertEquals(customer.email(), read(user, "$.email"));
        assertThat(user.body()).doesNotContain("password");

        HttpResponse<String> renamed = put("/users/" + customer.id() + "/name", customer.bearer(),
                "{ \"name\": \"Novo Nome\" }");
        assertEquals(200, renamed.statusCode(), renamed.body());
        assertEquals("Novo Nome", read(renamed, "$.name"));

        String newEmail = "changed-" + customer.email();
        HttpResponse<String> emailChanged = put("/users/" + customer.id() + "/email", customer.bearer(),
                "{ \"email\": \"%s\" }".formatted(newEmail));
        assertEquals(200, emailChanged.statusCode(), emailChanged.body());

        HttpResponse<String> passwordChanged = put("/users/" + customer.id() + "/password", customer.bearer(),
                "{ \"password\": \"outraSenha123\" }");
        assertEquals(200, passwordChanged.statusCode(), passwordChanged.body());

        HttpResponse<String> login = login(newEmail, "outraSenha123");
        assertEquals(200, login.statusCode(), login.body());
        assertEquals("Bearer", read(login, "$.type"));
        assertEquals(900, (int) read(login, "$.expiresIn"));

        HttpResponse<String> refreshed = post("/auth/refresh", null,
                "{ \"refreshToken\": \"%s\" }".formatted((String) read(login, "$.refreshToken")));
        assertEquals(200, refreshed.statusCode(), refreshed.body());

        HttpResponse<String> logout = post("/auth/logout", null,
                "{ \"refreshToken\": \"%s\" }".formatted((String) read(refreshed, "$.refreshToken")));
        assertEquals(204, logout.statusCode(), logout.body());

        assertError(login(newEmail, "senha-errada"), 401);
        assertError(get("/users/" + customer.id(), newCustomer().bearer()), 403);
    }

    @Test
    void shouldServeTheCatalogRoutes() {
        String admin = adminBearer();
        String productId = createProduct(admin, "Teclado", "100.00");

        assertPage(get("/products", null));

        HttpResponse<String> product = get("/products/" + productId, null);
        assertEquals(200, product.statusCode(), product.body());
        assertEquals("Teclado", read(product, "$.name"));

        HttpResponse<String> updated = put("/products/" + productId, admin, """
                { "name": "Teclado", "description": "Mecanico", "price": 120.00 }
                """);
        assertEquals(200, updated.statusCode(), updated.body());

        assertError(post("/products", newCustomer().bearer(), """
                { "name": "Mouse", "description": "Sem fio", "price": 50.00 }
                """), 403);
        assertError(post("/products", admin, """
                { "name": "", "description": "Sem nome", "price": -1 }
                """), 400);

        assertEquals(204, delete("/products/" + productId, admin).statusCode());
        assertError(get("/products/" + productId, null), 404);
    }

    @Test
    void shouldServeTheCartRoutesAndCheckout() {
        String productId = createProduct(adminBearer(), "Monitor", "900.00");
        Customer customer = newCustomer();

        HttpResponse<String> added = post("/cart/items", customer.bearer(), """
                { "productId": "%s", "quantity": 1 }
                """.formatted(productId));
        assertEquals(200, added.statusCode(), added.body());

        HttpResponse<String> changed = put("/cart/items/" + productId, customer.bearer(), "{ \"quantity\": 2 }");
        assertEquals(200, changed.statusCode(), changed.body());
        assertEquals(2, (int) read(changed, "$.items[0].quantity"));

        HttpResponse<String> cart = get("/cart", customer.bearer());
        assertEquals(200, cart.statusCode(), cart.body());

        HttpResponse<String> removed = delete("/cart/items/" + productId, customer.bearer());
        assertEquals(200, removed.statusCode(), removed.body());

        post("/cart/items", customer.bearer(), """
                { "productId": "%s", "quantity": 1 }
                """.formatted(productId));
        HttpResponse<String> checkout = post("/cart/checkout", customer.bearer(), "");
        assertEquals(200, checkout.statusCode(), checkout.body());
        assertEquals("PENDING", read(checkout, "$.status"));
    }

    @Test
    void shouldServeTheOrderLifecycleAndPaymentRoutes() {
        String admin = adminBearer();
        String productId = createProduct(admin, "Cadeira", "300.00");
        Customer customer = newCustomer();
        String items = """
                { "items": [ { "productId": "%s", "quantity": 1 } ] }
                """.formatted(productId);

        HttpResponse<String> created = post("/orders", customer.bearer(), items);
        assertEquals(201, created.statusCode(), created.body());
        String orderId = read(created, "$.id");
        assertThat(created.headers().firstValue("Location")).hasValueSatisfying(
                location -> assertThat(location).endsWith("/orders/" + orderId));

        assertPage(get("/orders", customer.bearer()));
        assertEquals(200, get("/orders/" + orderId, customer.bearer()).statusCode());
        assertError(get("/orders/" + orderId, newCustomer().bearer()), 404);

        assertEquals("CONFIRMED", read(post("/orders/" + orderId + "/confirm", customer.bearer(), ""), "$.status"));
        assertError(post("/orders/" + orderId + "/ship", customer.bearer(), ""), 403);
        assertEquals("SHIPPED", read(post("/orders/" + orderId + "/ship", admin, ""), "$.status"));
        assertEquals("DELIVERED", read(post("/orders/" + orderId + "/deliver", admin, ""), "$.status"));
        assertError(post("/orders/" + orderId + "/cancel", customer.bearer(), ""), 409);

        String cancellable = read(post("/orders", customer.bearer(), items), "$.id");
        assertEquals("CANCELLED", read(post("/orders/" + cancellable + "/cancel", customer.bearer(), ""), "$.status"));

        HttpResponse<String> payment = Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .until(() -> get("/payments/orders/" + orderId, customer.bearer()),
                        response -> response.statusCode() == 200);
        String paymentId = read(payment, "$.id");
        assertEquals(200, get("/payments/" + paymentId, customer.bearer()).statusCode());
    }

    @Test
    void shouldServeTheSalesReportsToAdministratorsOnly() {
        String admin = adminBearer();
        String today = LocalDate.now().toString();

        HttpResponse<String> daily = get("/admin/sales/daily?from=" + today + "&to=" + today, admin);
        assertEquals(200, daily.statusCode(), daily.body());

        HttpResponse<String> top = get("/admin/sales/top-products?limit=5", admin);
        assertEquals(200, top.statusCode(), top.body());

        assertError(get("/admin/sales/top-products", newCustomer().bearer()), 403);
    }

    @Test
    void shouldRefuseRequestsWithoutCredentialInTheStandardErrorFormat() {
        assertError(get("/orders", null), 401);
        assertError(get("/cart", null), 401);
    }
}
