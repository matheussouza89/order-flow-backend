package com.matheus.orderFlow.integration;

import com.jayway.jsonpath.JsonPath;
import com.matheus.orderFlow.sales.OrderViewRebuilder;
import org.awaitility.Awaitility;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderViewRebuilderIT extends AbstractIntegrationTest {

    private static final String VIEWS = "order_views";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OrderViewRebuilder rebuilder;

    @Test
    void shouldRebuildTheViewAsTheProjectionWroteIt() throws Exception {
        String orderId = createOrder(createProduct("Teclado", "100.00"));
        transition(orderId, "confirm");
        transition(orderId, "ship");
        UUID id = UUID.fromString(orderId);
        awaitStatus(id, "SHIPPED");
        Document before = viewOf(id);

        mongoTemplate.remove(new Query(), VIEWS);
        rebuilder.rebuild();
        Document after = viewOf(id);

        assertEquals(before.getString("status"), after.getString("status"));
        assertEquals(before.getString("userId"), after.getString("userId"));
        assertEquals(before.getLong("version").longValue(), after.getLong("version").longValue());
        assertThat(((Decimal128) after.get("total")).bigDecimalValue()).isEqualByComparingTo("100.00");
        assertEquals(before.getDate("confirmedAt"), after.getDate("confirmedAt"));
        assertTrue(Duration.between(before.getDate("updatedAt").toInstant(), after.getDate("updatedAt").toInstant())
                .abs().toMillis() <= 1);

        Document lineBefore = before.getList("items", Document.class).get(0);
        Document lineAfter = after.getList("items", Document.class).get(0);
        assertEquals(lineBefore.getString("productName"), lineAfter.getString("productName"));
        assertEquals(lineBefore.getInteger("quantity"), lineAfter.getInteger("quantity"));
        assertThat(((Decimal128) lineAfter.get("unitPrice")).bigDecimalValue()).isEqualByComparingTo("100.00");
    }

    @Test
    void shouldRebuildOrdersThatPredateTheEventsIncludingTheirItems() {
        UUID legacyId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO orders (id, user_id, status, total, version, created_at, updated_at)
                VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), 'CONFIRMED', 100.00, 0, NOW(6), NOW(6))
                """, legacyId.toString(), UUID.randomUUID().toString());
        jdbcTemplate.update("""
                INSERT INTO order_items (id, order_id, product_id, product_name, unit_price, quantity, subtotal)
                VALUES (UUID_TO_BIN(UUID()), UUID_TO_BIN(?), UUID_TO_BIN(?), 'Teclado', 100.00, 1, 100.00)
                """, legacyId.toString(), UUID.randomUUID().toString());

        rebuilder.rebuild();

        Document view = viewOf(legacyId);
        assertEquals("CONFIRMED", view.getString("status"));
        assertEquals(0L, view.getLong("version").longValue());
        assertEquals("Teclado", view.getList("items", Document.class).get(0).getString("productName"));
        assertNotNull(view.getDate("confirmedAt"));
    }

    @Test
    void shouldNotRebuildOrdersThatWereNeverConfirmed() throws Exception {
        UUID pendingId = UUID.fromString(createOrder(createProduct("Mouse", "50.00")));

        rebuilder.rebuild();

        assertNull(viewOf(pendingId));
    }

    private void awaitStatus(UUID orderId, String status) {
        Awaitility.await().atMost(Duration.ofSeconds(40)).until(() -> {
            Document view = viewOf(orderId);
            return view != null && status.equals(view.getString("status"));
        });
    }

    private Document viewOf(UUID orderId) {
        return mongoTemplate.findById(orderId.toString(), Document.class, VIEWS);
    }

    private String createProduct(String name, String price) throws Exception {
        String response = mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "%s",
                                    "description": "Descricao",
                                    "price": %s
                                }
                                """.formatted(name, price)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return JsonPath.read(response, "$.id");
    }

    private String createOrder(String productId) throws Exception {
        String response = mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "items": [ { "productId": "%s", "quantity": 1 } ] }
                                """.formatted(productId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return JsonPath.read(response, "$.id");
    }

    private void transition(String orderId, String action) throws Exception {
        mockMvc.perform(post("/orders/{id}/{action}", orderId, action))
                .andExpect(status().isOk());
    }
}
