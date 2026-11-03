package com.matheus.orderFlow.gateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DocumentationIT {
    private static final WireMockServer MONOLITH = new WireMockServer(options().dynamicPort());
    private static final WireMockServer SALES = new WireMockServer(options().dynamicPort());
    private static final WireMockServer PAYMENT = new WireMockServer(options().dynamicPort());
    private static final String MONOLITH_DOCS = """
            {"openapi":"3.1.0","info":{"title":"OrderFlow API"},"paths":{"/orders":{}}}""";
    private static final String SALES_DOCS = """
            {"openapi":"3.1.0","info":{"title":"OrderFlow Sales API"},"paths":{"/admin/sales/daily":{}}}""";
    private static final String PAYMENT_DOCS = """
            {"openapi":"3.1.0","info":{"title":"OrderFlow Payments API"},"paths":{"/payments/{id}":{}}}""";

    static {
        MONOLITH.start();
        SALES.start();
        PAYMENT.start();
    }

    @DynamicPropertySource
    static void routeToTheFakeServices(DynamicPropertyRegistry registry) {
        registry.add("MONOLITH_URL", MONOLITH::baseUrl);
        registry.add("SALES_URL", SALES::baseUrl);
        registry.add("PAYMENT_URL", PAYMENT::baseUrl);
    }

    @AfterAll
    static void stopTheFakeServices() {
        MONOLITH.stop();
        SALES.stop();
        PAYMENT.stop();
    }

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private HttpResponse<String> fetch(String path) throws IOException, InterruptedException {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void shouldServeASingleDocumentationPage() throws Exception {
        HttpResponse<String> response = fetch("/swagger-ui.html");

        assertEquals(200, response.statusCode());
        assertThat(response.body()).containsIgnoringCase("swagger");
    }

    @Test
    void shouldListOneEntryPerService() throws Exception {
        HttpResponse<String> response = fetch("/v3/api-docs/swagger-config");

        List<Map<String, String>> urls = JsonPath.read(response.body(), "$.urls");

        assertThat(urls).containsExactlyInAnyOrder(
                Map.of("name", "monolith", "url", "/v3/api-docs/monolith"),
                Map.of("name", "payment", "url", "/v3/api-docs/payment"),
                Map.of("name", "sales", "url", "/v3/api-docs/sales"));
    }

    @Test
    void shouldFetchEachServiceDocumentThroughTheSingleEntry() throws Exception {
        serveDocs(MONOLITH, MONOLITH_DOCS);
        serveDocs(SALES, SALES_DOCS);
        serveDocs(PAYMENT, PAYMENT_DOCS);

        assertEquals(MONOLITH_DOCS, fetch("/v3/api-docs/monolith").body());
        assertEquals(SALES_DOCS, fetch("/v3/api-docs/sales").body());
        assertEquals(PAYMENT_DOCS, fetch("/v3/api-docs/payment").body());
    }

    private static void serveDocs(WireMockServer service, String document) {
        service.stubFor(get(urlEqualTo("/v3/api-docs"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody(document)));
    }
}
