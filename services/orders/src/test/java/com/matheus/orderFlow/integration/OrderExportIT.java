package com.matheus.orderFlow.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderExportIT extends AbstractIntegrationTest {

    private static final String EXPORT = "/internal/orders/export";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @Test
    void shouldExportOnlyOrdersThatWereConfirmed() throws Exception {
        String productId = catalogProduct("Teclado", "100.00");
        String pending = createOrder(productId);
        String confirmed = confirmedOrder(productId);
        String shipped = confirmedOrder(productId);
        transition(shipped, "ship");
        String cancelledBeforeConfirming = createOrder(productId);
        transition(cancelledBeforeConfirming, "cancel");
        String cancelledAfterConfirming = confirmedOrder(productId);
        transition(cancelledAfterConfirming, "cancel");

        List<String> exported = JsonPath.read(export(""), "$.content[*].orderId");

        assertThat(exported).containsExactlyInAnyOrder(confirmed, shipped, cancelledAfterConfirming);
        assertThat(exported).doesNotContain(pending, cancelledBeforeConfirming);
    }

    @Test
    void shouldCarryWhatTheReportsNeedWithTheMomentOfConfirmation() throws Exception {
        String orderId = confirmedOrder(catalogProduct("Teclado", "100.00"));
        transition(orderId, "ship");

        String response = export("");

        assertEquals(orderId, JsonPath.read(response, "$.content[0].orderId"));
        assertEquals(DefaultAuthenticationConfiguration.DEFAULT_USER_ID.toString(),
                JsonPath.read(response, "$.content[0].userId"));
        assertEquals("SHIPPED", JsonPath.read(response, "$.content[0].status"));
        assertEquals(100.0, ((Number) JsonPath.read(response, "$.content[0].total")).doubleValue());
        assertEquals(2, ((Number) JsonPath.read(response, "$.content[0].version")).intValue());
        assertEquals("Teclado", JsonPath.read(response, "$.content[0].items[0].productName"));
        assertEquals(100.0, ((Number) JsonPath.read(response, "$.content[0].items[0].unitPrice")).doubleValue());
        assertEquals(1, ((Number) JsonPath.read(response, "$.content[0].items[0].quantity")).intValue());
        assertEquals(100.0, ((Number) JsonPath.read(response, "$.content[0].items[0].subtotal")).doubleValue());
        assertThat((String) JsonPath.read(response, "$.content[0].items[0].productId")).isNotBlank();
        assertThat((String) JsonPath.read(response, "$.content[0].updatedAt")).isNotBlank();

        String confirmation = jdbcTemplate.queryForObject(
                "SELECT payload FROM outbox_messages WHERE routing_key = 'order.confirmed'", String.class);
        assertEquals(Instant.parse(JsonPath.read(confirmation, "$.occurredAt")),
                Instant.parse(JsonPath.read(response, "$.content[0].confirmedAt")));
    }

    @Test
    void shouldUseTheCreationTimeForOrdersConfirmedBeforeTheOutboxExisted() throws Exception {
        UUID legacyId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO orders (id, user_id, status, total, version, created_at, updated_at)
                VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), 'CONFIRMED', 100.00, 0, NOW(6), NOW(6))
                """, legacyId.toString(), UUID.randomUUID().toString());
        jdbcTemplate.update("""
                INSERT INTO order_items (id, order_id, product_id, product_name, unit_price, quantity, subtotal)
                VALUES (UUID_TO_BIN(UUID()), UUID_TO_BIN(?), UUID_TO_BIN(?), 'Teclado', 100.00, 1, 100.00)
                """, legacyId.toString(), UUID.randomUUID().toString());

        String response = export("");
        String order = mockMvc.perform(get("/orders/{id}", legacyId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertEquals(legacyId.toString(), JsonPath.read(response, "$.content[0].orderId"));
        assertEquals("Teclado", JsonPath.read(response, "$.content[0].items[0].productName"));
        assertEquals(Instant.parse(JsonPath.read(order, "$.createdAt")),
                Instant.parse(JsonPath.read(response, "$.content[0].confirmedAt")));
    }

    @Test
    void shouldHandOutOneHundredOrdersPerPageByDefault() throws Exception {
        confirmedOrder(catalogProduct("Teclado", "100.00"));

        assertEquals(100, (int) JsonPath.read(export(""), "$.size"));
    }

    @Test
    void shouldWalkThroughEveryConfirmedOrderPageByPage() throws Exception {
        String productId = catalogProduct("Teclado", "100.00");
        Set<String> confirmed = Set.of(confirmedOrder(productId), confirmedOrder(productId), confirmedOrder(productId));

        String first = export("?page=0&size=2");
        String second = export("?page=1&size=2");

        assertEquals(3, (int) JsonPath.read(first, "$.totalElements"));
        assertEquals(2, (int) JsonPath.read(first, "$.totalPages"));
        Set<String> walked = new HashSet<>(JsonPath.<List<String>>read(first, "$.content[*].orderId"));
        walked.addAll(JsonPath.<List<String>>read(second, "$.content[*].orderId"));
        assertEquals(confirmed, walked);
    }

    @Test
    void shouldAcceptPagesOfUpToFiveHundredOrders() throws Exception {
        mockMvc.perform(get(EXPORT).param("size", "500"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectASizeOutsideOneToFiveHundred() throws Exception {
        mockMvc.perform(get(EXPORT).param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.size").isNotEmpty());

        mockMvc.perform(get(EXPORT).param("size", "501"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.size").isNotEmpty());
    }

    @Test
    void shouldRejectANegativePage() throws Exception {
        mockMvc.perform(get(EXPORT).param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.page").isNotEmpty());
    }

    @Test
    void shouldForbidRegularUsers() throws Exception {
        mockMvc.perform(get(EXPORT).header("Authorization", asUser()))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRefuseACallWithoutCredential() throws Exception {
        MockMvc anonymous = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(springSecurityFilterChain)
                .build();

        anonymous.perform(get(EXPORT))
                .andExpect(status().isUnauthorized());
    }

    private String export(String query) throws Exception {
        return mockMvc.perform(get(EXPORT + query))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String createOrder(String productId) throws Exception {
        String response = mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "items": [ { "productId": "%s", "quantity": 1 } ] }
                                """.formatted(productId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.read(response, "$.id");
    }

    private String confirmedOrder(String productId) throws Exception {
        String orderId = createOrder(productId);
        transition(orderId, "confirm");
        return orderId;
    }

    private void transition(String orderId, String action) throws Exception {
        mockMvc.perform(post("/orders/{id}/{action}", orderId, action))
                .andExpect(status().isOk());
    }
}
