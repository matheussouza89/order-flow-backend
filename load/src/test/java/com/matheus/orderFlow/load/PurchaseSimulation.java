package com.matheus.orderFlow.load;

import io.gatling.javaapi.core.Assertion;
import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.details;
import static io.gatling.javaapi.core.CoreDsl.during;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.group;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.listFeeder;
import static io.gatling.javaapi.core.CoreDsl.rampUsers;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

public class PurchaseSimulation extends Simulation {
    private static final String BASE_URL = System.getProperty("baseUrl", "http://localhost:8080");
    private static final int USERS = Integer.getInteger("users", 50);
    private static final Duration DURATION = Duration.ofSeconds(Integer.getInteger("duration", 300));
    private static final boolean CHECK_LATENCY = Boolean.parseBoolean(System.getProperty("checkLatency", "true"));
    private static final String ADMIN_EMAIL = System.getProperty("adminEmail", "admin@orderflow.local");
    private static final String ADMIN_PASSWORD = System.getProperty("adminPassword", "change-me-in-production");
    private static final String PASSWORD = "senhaSegura1";
    private static final int PRODUCTS = 20;
    private static final Duration RAMP_UP = Duration.ofSeconds(30);
    private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern TOKEN = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");

    private static final List<String> PRODUCT_IDS = new ArrayList<>();
    private static final String RUN = UUID.randomUUID().toString().substring(0, 8);
    private static final List<Map<String, Object>> CUSTOMERS = IntStream.range(0, USERS)
            .<Map<String, Object>>mapToObj(index -> Map.of("email", "load-" + RUN + "-" + index + "@example.com"))
            .toList();

    private final HttpProtocolBuilder protocol = http.baseUrl(BASE_URL)
            .contentTypeHeader("application/json")
            .acceptHeader("application/json");

    private final ChainBuilder storefront = group("storefront").on(
            exec(http("list products").get("/products?page=0&size=20").check(status().is(200)))
                    .exec(session -> session.set("productId",
                            PRODUCT_IDS.get(ThreadLocalRandom.current().nextInt(PRODUCT_IDS.size()))))
                    .exec(http("product detail").get("/products/#{productId}").check(status().is(200))));

    private final ChainBuilder cartAndCheckout = exec(
            http("add to cart").post("/cart/items")
                    .header("Authorization", "Bearer #{token}")
                    .body(StringBody("{ \"productId\": \"#{productId}\", \"quantity\": 1 }"))
                    .check(status().is(200)))
            .pause(Duration.ofMillis(500), Duration.ofMillis(1500))
            .exec(http("checkout").post("/cart/checkout")
                    .header("Authorization", "Bearer #{token}")
                    .check(status().is(200), jsonPath("$.id").saveAs("orderId")));

    private final ChainBuilder orderQueries = exec(
            http("list orders").get("/orders?page=0&size=10")
                    .header("Authorization", "Bearer #{token}")
                    .check(status().is(200)))
            .exec(http("order detail").get("/orders/#{orderId}")
                    .header("Authorization", "Bearer #{token}")
                    .check(status().is(200)));

    private final ScenarioBuilder purchase = scenario("purchase")
            .feed(listFeeder(CUSTOMERS).queue())
            .exec(http("login").post("/auth/login")
                    .body(StringBody("{ \"email\": \"#{email}\", \"password\": \"" + PASSWORD + "\" }"))
                    .check(status().is(200), jsonPath("$.token").saveAs("token")))
            .exec(during(DURATION).on(
                    exec(storefront)
                            .pause(Duration.ofMillis(500), Duration.ofMillis(1500))
                            .exec(cartAndCheckout)
                            .pause(Duration.ofMillis(500), Duration.ofMillis(1500))
                            .exec(orderQueries)
                            .pause(Duration.ofMillis(500), Duration.ofMillis(1500))));

    {
        setUp(purchase.injectOpen(rampUsers(USERS).during(RAMP_UP)))
                .protocols(protocol)
                .maxDuration(DURATION.plus(RAMP_UP).plusMinutes(1))
                .assertions(assertions());
    }

    private static List<Assertion> assertions() {
        Assertion failures = global().failedRequests().percent().lt(1.0);

        if (!CHECK_LATENCY) {
            return List.of(failures);
        }

        return Stream.of(
                failures,
                details("checkout").responseTime().percentile(95.0).lte(1000),
                details("storefront", "list products").responseTime().percentile(95.0).lte(300),
                details("storefront", "product detail").responseTime().percentile(95.0).lte(300)
        ).toList();
    }

    @Override
    public void before() {
        HttpClient client = HttpClient.newHttpClient();
        String admin = "Bearer " + extract(TOKEN, call(client, "POST", "/auth/login", null, """
                { "email": "%s", "password": "%s" }
                """.formatted(ADMIN_EMAIL, ADMIN_PASSWORD), 200));

        for (int index = 0; index < PRODUCTS; index++) {
            PRODUCT_IDS.add(extract(ID, call(client, "POST", "/products", admin, """
                    { "name": "Produto %d", "description": "Carga %s", "price": %d.90 }
                    """.formatted(index, RUN, 10 + index), 201)));
        }

        for (Map<String, Object> customer : CUSTOMERS) {
            call(client, "POST", "/auth/register", null, """
                    { "name": "Cliente de carga", "email": "%s", "password": "%s" }
                    """.formatted(customer.get("email"), PASSWORD), 201);
        }
    }

    private static String call(HttpClient client, String method, String path, String bearer, String json,
                               int expectedStatus) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(BASE_URL + path))
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(json));

        if (bearer != null) {
            request.header("Authorization", bearer);
        }

        try {
            HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != expectedStatus) {
                throw new IllegalStateException(method + " " + path + " answered "
                        + response.statusCode() + ": " + response.body());
            }

            return response.body();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private static String extract(Pattern pattern, String body) {
        Matcher matcher = pattern.matcher(body);

        if (!matcher.find()) {
            throw new IllegalStateException("Field not found in " + body);
        }

        return matcher.group(1);
    }
}
