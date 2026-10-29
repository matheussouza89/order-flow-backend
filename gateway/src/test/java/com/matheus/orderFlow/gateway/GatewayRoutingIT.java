package com.matheus.orderFlow.gateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingIT {
    private static final WireMockServer MONOLITH = new WireMockServer(options().port(freePort()));
    private static final String TOKEN = "Bearer any-token";
    private static final String CONFLICT = """
            {"status":409,"message":"Only pending orders can be confirmed","errors":{}}""";

    static {
        MONOLITH.start();
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    @DynamicPropertySource
    static void routeToTheFakeMonolith(DynamicPropertyRegistry registry) {
        registry.add("MONOLITH_URL", MONOLITH::baseUrl);
    }

    @AfterAll
    static void stopTheFakeMonolith() {
        MONOLITH.stop();
    }

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void resetTheFakeMonolith() {
        MONOLITH.resetAll();
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException {
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder to(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
    }

    @Test
    void shouldForwardMethodPathBodyAndAuthorizationUntouched() throws Exception {
        MONOLITH.stubFor(post(urlEqualTo("/orders?source=cart"))
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"42\"}")));

        HttpResponse<String> response = send(to("/orders?source=cart")
                .header("Authorization", TOKEN)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"items\":[]}")));

        assertEquals(201, response.statusCode());
        assertEquals("{\"id\":\"42\"}", response.body());
        MONOLITH.verify(postRequestedFor(urlEqualTo("/orders?source=cart"))
                .withHeader("Authorization", equalTo(TOKEN))
                .withHeader("Host", equalTo("localhost:" + port))
                .withRequestBody(equalToJson("{\"items\":[]}")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/auth/login", "/users/1", "/products", "/products/1", "/cart",
            "/orders", "/payments/orders/1", "/admin/sales/daily"})
    void shouldRouteEveryPublicPrefixToTheMonolith(String path) throws Exception {
        MONOLITH.stubFor(WireMock.any(urlEqualTo(path)).willReturn(aResponse().withStatus(200)));

        assertEquals(200, send(to(path).GET()).statusCode());
    }

    @Test
    void shouldPassTheServiceErrorsThroughUnchanged() throws Exception {
        MONOLITH.stubFor(post(urlEqualTo("/orders/1/confirm"))
                .willReturn(aResponse().withStatus(409).withHeader("Content-Type", "application/json")
                        .withBody(CONFLICT)));

        HttpResponse<String> response = send(to("/orders/1/confirm")
                .header("Authorization", TOKEN)
                .POST(HttpRequest.BodyPublishers.noBody()));

        assertEquals(409, response.statusCode());
        assertEquals(CONFLICT, response.body());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/internal/orders/export", "/.well-known/jwks.json", "/actuator/env",
            "/anything-else"})
    void shouldNotExposeRoutesThatStayInsideTheNetwork(String path) throws Exception {
        HttpResponse<String> response = send(to(path).header("Authorization", TOKEN).GET());

        assertEquals(404, response.statusCode());
        assertThat(response.body()).contains("\"status\":404");
        MONOLITH.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void shouldKeepItsOwnHealthOpen() throws Exception {
        assertEquals(200, send(to("/actuator/health").GET()).statusCode());
    }

    @Test
    void shouldAnswerServiceUnavailableWhenTheServiceIsDown() throws Exception {
        MONOLITH.stop();

        try {
            HttpResponse<String> response = send(to("/orders").header("Authorization", TOKEN).GET());

            assertEquals(503, response.statusCode());
            assertThat(response.body()).contains("\"status\":503", "Service unavailable");
        } finally {
            MONOLITH.start();
        }
    }

    @Test
    void shouldAnswerGatewayTimeoutWhenTheServiceTakesLongerThanFourAndAHalfSeconds() throws Exception {
        MONOLITH.stubFor(get(urlEqualTo("/orders"))
                .willReturn(aResponse().withStatus(200).withFixedDelay(6_000)));

        Instant start = Instant.now();
        HttpResponse<String> response = send(to("/orders").header("Authorization", TOKEN).GET());
        Duration elapsed = Duration.between(start, Instant.now());

        assertEquals(504, response.statusCode());
        assertThat(response.body()).contains("\"status\":504", "Service did not respond in time");
        assertTrue(elapsed.compareTo(Duration.ofSeconds(5)) < 0, "took " + elapsed);
    }
}
