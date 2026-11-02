package com.matheus.orderFlow.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SalesRebuildIT extends AbstractIntegrationTest {

    private static final WireMockServer ORDERS = new WireMockServer(options().port(freePort()));
    private static final String EXPORT = "/internal/orders/export";
    private static final String REBUILD = "/admin/sales/rebuild";
    private static final String VIEWS = "order_views";

    static {
        ORDERS.start();
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    @DynamicPropertySource
    static void exportFromTheFakeOrdersService(DynamicPropertyRegistry registry) {
        registry.add("spring.http.serviceclient.orders.base-url", ORDERS::baseUrl);
    }

    @AfterAll
    static void stopTheFakeOrdersService() {
        ORDERS.stop();
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void resetTheFakeOrdersService() {
        ORDERS.resetAll();
    }

    @Test
    void shouldRebuildTheReportsFromAnEmptyView() throws Exception {
        UUID keyboard = UUID.randomUUID();
        exportPage(0, 1,
                order(UUID.randomUUID(), "SHIPPED", 2, "2026-10-10T15:00:00Z", line(keyboard, "Teclado", "100.00", 2)),
                order(UUID.randomUUID(), "CONFIRMED", 1, "2026-10-12T15:00:00Z", line(keyboard, "Teclado", "100.00", 1)),
                order(UUID.randomUUID(), "CANCELLED", 2, "2026-10-12T16:00:00Z", line(UUID.randomUUID(), "Monitor", "900.00", 1)));

        mockMvc.perform(post(REBUILD).header("Authorization", asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rebuilt").value(3));

        mockMvc.perform(MockMvcRequestBuilders.get("/admin/sales/daily")
                        .param("from", "2026-10-10").param("to", "2026-10-12")
                        .header("Authorization", asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].total").value(200.00))
                .andExpect(jsonPath("$[1].total").value(0.00))
                .andExpect(jsonPath("$[2].total").value(100.00));

        mockMvc.perform(MockMvcRequestBuilders.get("/admin/sales/top-products")
                        .header("Authorization", asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].productName").value("Teclado"))
                .andExpect(jsonPath("$[0].quantity").value(3));
    }

    @Test
    void shouldWalkThroughEveryPageOfTheExport() throws Exception {
        exportPage(0, 2, order(UUID.randomUUID(), "CONFIRMED", 1, "2026-10-10T15:00:00Z",
                line(UUID.randomUUID(), "Teclado", "100.00", 1)));
        exportPage(1, 2, order(UUID.randomUUID(), "CONFIRMED", 1, "2026-10-11T15:00:00Z",
                line(UUID.randomUUID(), "Mouse", "50.00", 1)));

        mockMvc.perform(post(REBUILD).header("Authorization", asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rebuilt").value(2));

        assertEquals(2, mongoTemplate.getCollection(VIEWS).countDocuments());
    }

    @Test
    void shouldNotReplaceANewerDocumentWithOlderData() throws Exception {
        UUID orderId = UUID.randomUUID();
        mongoTemplate.getCollection(VIEWS).insertOne(new Document("_id", orderId.toString())
                .append("userId", UUID.randomUUID().toString())
                .append("status", "DELIVERED")
                .append("total", new Decimal128(new BigDecimal("100.00")))
                .append("items", List.of())
                .append("version", 3L)
                .append("confirmedAt", Date.from(Instant.parse("2026-10-10T15:00:00Z")))
                .append("updatedAt", Date.from(Instant.parse("2026-10-12T15:00:00Z"))));
        exportPage(0, 1, order(orderId, "SHIPPED", 2, "2026-10-10T15:00:00Z",
                line(UUID.randomUUID(), "Teclado", "100.00", 1)));

        mockMvc.perform(post(REBUILD).header("Authorization", asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rebuilt").value(0));

        Document stored = mongoTemplate.getCollection(VIEWS).find(new Document("_id", orderId.toString())).first();
        assertEquals("DELIVERED", stored.getString("status"));
        assertEquals(3L, stored.getLong("version"));
    }

    @Test
    void shouldCallTheExportWithTheAdministratorCredential() throws Exception {
        exportPage(0, 1);
        String admin = asAdmin();

        mockMvc.perform(post(REBUILD).header("Authorization", admin))
                .andExpect(status().isOk());

        ORDERS.verify(getRequestedFor(urlPathEqualTo(EXPORT)).withHeader("Authorization", equalTo(admin)));
    }

    @Test
    void shouldAnswerServiceUnavailableWhenTheOrdersServiceIsDown() throws Exception {
        ORDERS.stop();

        try {
            mockMvc.perform(post(REBUILD).header("Authorization", asAdmin()))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.status").value(503))
                    .andExpect(jsonPath("$.message").value("Service unavailable: orders"));
        } finally {
            ORDERS.start();
        }
    }

    @Test
    void shouldAnswerServiceUnavailableWhenTheOrdersServiceFails() throws Exception {
        ORDERS.stubFor(get(urlPathEqualTo(EXPORT)).willReturn(aResponse().withStatus(500)));

        mockMvc.perform(post(REBUILD).header("Authorization", asAdmin()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }

    @Test
    void shouldPassARefusalOfTheExportThroughUnchanged() throws Exception {
        String refusal = """
                {"status":403,"message":"You do not have permission to perform this operation","errors":{}}""";
        ORDERS.stubFor(get(urlPathEqualTo(EXPORT)).willReturn(aResponse().withStatus(403)
                .withHeader("Content-Type", "application/json").withBody(refusal)));

        mockMvc.perform(post(REBUILD).header("Authorization", asAdmin()))
                .andExpect(status().isForbidden())
                .andExpect(content().json(refusal));
    }

    @Test
    void shouldForbidRegularUsersWithoutCallingTheExport() throws Exception {
        mockMvc.perform(post(REBUILD).header("Authorization", asUser()))
                .andExpect(status().isForbidden());

        ORDERS.verify(0, anyRequestedFor(anyUrl()));
        assertThat(mongoTemplate.getCollection(VIEWS).countDocuments()).isZero();
    }

    private void exportPage(int page, int totalPages, String... orders) {
        ORDERS.stubFor(get(urlPathEqualTo(EXPORT))
                .withQueryParam("page", equalTo(String.valueOf(page)))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"content":[%s],"page":%d,"size":100,"totalElements":%d,"totalPages":%d}
                                """.formatted(String.join(",", orders), page, orders.length, totalPages))));
    }

    private static String order(UUID orderId, String status, long version, String confirmedAt, Line line) {
        return """
                {"orderId":"%s","userId":"%s","status":"%s","total":%s,"items":[%s],
                 "version":%d,"confirmedAt":"%s","updatedAt":"%s"}
                """.formatted(orderId, UUID.randomUUID(), status, line.subtotal(), line.json(),
                version, confirmedAt, confirmedAt);
    }

    private static Line line(UUID productId, String name, String unitPrice, int quantity) {
        return new Line(productId, name, new BigDecimal(unitPrice), quantity);
    }

    private record Line(UUID productId, String name, BigDecimal unitPrice, int quantity) {
        BigDecimal subtotal() {
            return unitPrice.multiply(BigDecimal.valueOf(quantity));
        }

        String json() {
            return """
                    {"id":"%s","productId":"%s","productName":"%s","unitPrice":%s,"quantity":%d,"subtotal":%s}
                    """.formatted(UUID.randomUUID(), productId, name, unitPrice, quantity, subtotal());
        }
    }
}
