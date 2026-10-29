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
    private static final String MONOLITH_DOCS = """
            {"openapi":"3.1.0","info":{"title":"OrderFlow API"},"paths":{"/orders":{}}}""";

    static {
        MONOLITH.start();
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

        assertThat(urls).contains(Map.of("name", "monolith", "url", "/v3/api-docs/monolith"));
    }

    @Test
    void shouldFetchEachServiceDocumentThroughTheSingleEntry() throws Exception {
        MONOLITH.stubFor(get(urlEqualTo("/v3/api-docs"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody(MONOLITH_DOCS)));

        HttpResponse<String> response = fetch("/v3/api-docs/monolith");

        assertEquals(200, response.statusCode());
        assertEquals(MONOLITH_DOCS, response.body());
    }
}
