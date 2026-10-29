package com.matheus.orderFlow.e2e;

import com.jayway.jsonpath.JsonPath;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitStrategy;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

public abstract class AbstractE2E {
    private static final String ADMIN_EMAIL = "admin@orderflow.local";
    private static final String ADMIN_PASSWORD = "change-me-in-production";
    private static final int HTTP_PORT = 8080;

    private static final ComposeContainer COMPOSE = new ComposeContainer(new File("../docker-compose.yml"))
            .withBuild(true)
            .withRemoveVolumes(true)
            .withExposedService("gateway", HTTP_PORT, healthy())
            .withExposedService("monolith", HTTP_PORT, healthy());

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    static {
        COMPOSE.start();
        Runtime.getRuntime().addShutdownHook(new Thread(COMPOSE::stop));
    }

    private static WaitStrategy healthy() {
        return Wait.forHttp("/actuator/health").forStatusCode(200)
                .withStartupTimeout(Duration.ofMinutes(10));
    }

    protected record Customer(String id, String email, String token, String refreshToken) {
        String bearer() {
            return "Bearer " + token;
        }
    }

    protected static URI gateway(String path) {
        return service("gateway", path);
    }

    protected static URI service(String name, String path) {
        return URI.create("http://" + COMPOSE.getServiceHost(name, HTTP_PORT) + ":"
                + COMPOSE.getServicePort(name, HTTP_PORT) + path);
    }

    protected static HttpResponse<String> send(HttpRequest.Builder request) {
        try {
            return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private static HttpRequest.Builder to(String path, String bearer) {
        HttpRequest.Builder request = HttpRequest.newBuilder(gateway(path));
        return bearer == null ? request : request.header("Authorization", bearer);
    }

    protected static HttpResponse<String> get(String path, String bearer) {
        return send(to(path, bearer).GET());
    }

    protected static HttpResponse<String> delete(String path, String bearer) {
        return send(to(path, bearer).DELETE());
    }

    protected static HttpResponse<String> post(String path, String bearer, String json) {
        return send(to(path, bearer).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)));
    }

    protected static HttpResponse<String> put(String path, String bearer, String json) {
        return send(to(path, bearer).header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json)));
    }

    protected static <T> T read(HttpResponse<String> response, String path) {
        return JsonPath.read(response.body(), path);
    }

    protected static Customer newCustomer() {
        String email = "customer-" + UUID.randomUUID() + "@example.com";
        String password = "senhaSegura1";

        HttpResponse<String> registered = post("/auth/register", null, """
                { "name": "Cliente", "email": "%s", "password": "%s" }
                """.formatted(email, password));
        assertEquals(201, registered.statusCode(), registered.body());

        HttpResponse<String> login = login(email, password);

        return new Customer(read(registered, "$.id"), email, read(login, "$.token"), read(login, "$.refreshToken"));
    }

    protected static HttpResponse<String> login(String email, String password) {
        return post("/auth/login", null, """
                { "email": "%s", "password": "%s" }
                """.formatted(email, password));
    }

    protected static String adminBearer() {
        return "Bearer " + read(login(ADMIN_EMAIL, ADMIN_PASSWORD), "$.token");
    }

    protected static String createProduct(String adminBearer, String name, String price) {
        HttpResponse<String> created = post("/products", adminBearer, """
                { "name": "%s", "description": "Produto de teste", "price": %s }
                """.formatted(name, price));
        assertEquals(201, created.statusCode(), created.body());

        return read(created, "$.id");
    }
}
