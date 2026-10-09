package com.matheus.orderFlow.integration;

import com.jayway.jsonpath.JsonPath;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OutboxIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String createProduct() throws Exception {
        String response = mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Teclado",
                                    "description": "Mecanico",
                                    "price": 100.00
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.read(response, "$.id");
    }

    private String createOrder() throws Exception {
        String response = mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "items": [ { "productId": "%s", "quantity": 1 } ] }
                                """.formatted(createProduct())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.read(response, "$.id");
    }

    private long outboxCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_messages", Long.class);
    }

    private Map<String, Object> onlyOutboxRow() {
        return jdbcTemplate.queryForMap("SELECT * FROM outbox_messages LIMIT 1");
    }

    @Test
    void shouldRecordTheEventWhenTheOrderIsConfirmed() throws Exception {
        String orderId = createOrder();

        assertEquals(0, outboxCount());

        mockMvc.perform(post("/orders/{id}/confirm", orderId))
                .andExpect(status().isOk());

        Map<String, Object> row = onlyOutboxRow();

        assertEquals("order.confirmed", row.get("routing_key"));
        assertEquals("com.matheus.orderFlow.order.OrderConfirmedEvent", row.get("payload_type"));
        assertTrue(row.get("payload").toString().contains(orderId));
        assertEquals(0, ((Number) row.get("attempts")).intValue());
    }

    @Test
    void shouldPublishTheRecordedEventAndMarkIt() throws Exception {
        String orderId = createOrder();

        mockMvc.perform(post("/orders/{id}/confirm", orderId))
                .andExpect(status().isOk());

        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertEquals(0L, jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM outbox_messages WHERE published_at IS NULL",
                        Long.class)));
    }

    @Test
    void shouldReachTheConsumerThroughTheOutbox() throws Exception {
        String orderId = createOrder();

        mockMvc.perform(post("/orders/{id}/confirm", orderId))
                .andExpect(status().isOk());

        Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> mockMvc.perform(get("/payments/orders/{orderId}", orderId))
                        .andExpect(status().isOk()));
    }

    @Test
    void shouldNotRecordAnythingWhenTheTransitionIsRejected() throws Exception {
        String orderId = createOrder();

        mockMvc.perform(post("/orders/{id}/confirm", orderId)).andExpect(status().isOk());

        long afterFirstConfirm = outboxCount();

        mockMvc.perform(post("/orders/{id}/confirm", orderId))
                .andExpect(status().isConflict());

        assertEquals(afterFirstConfirm, outboxCount());
    }

    @Test
    void shouldNotRecordAnythingForAnOrderThatWasNeverConfirmed() throws Exception {
        createOrder();

        assertEquals(0, outboxCount());
    }

    @Test
    void shouldRecordOneMessagePerConfirmedOrder() throws Exception {
        String firstOrder = createOrder();
        String secondOrder = createOrder();

        mockMvc.perform(post("/orders/{id}/confirm", firstOrder)).andExpect(status().isOk());
        mockMvc.perform(post("/orders/{id}/confirm", secondOrder)).andExpect(status().isOk());

        assertEquals(2, outboxCount());
    }
}
