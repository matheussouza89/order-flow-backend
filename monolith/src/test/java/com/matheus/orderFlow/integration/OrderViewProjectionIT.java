package com.matheus.orderFlow.integration;

import com.matheus.orderFlow.contracts.order.OrderFacts;
import com.jayway.jsonpath.JsonPath;
import com.matheus.orderFlow.contracts.order.OrderConfirmedEvent;
import com.matheus.orderFlow.contracts.order.OrderDeliveredEvent;
import com.matheus.orderFlow.contracts.order.OrderShippedEvent;
import com.matheus.orderFlow.shared.messaging.RabbitMqConfig;
import org.awaitility.Awaitility;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderViewProjectionIT extends AbstractIntegrationTest {

    private static final String VIEWS = "order_views";
    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "_id", "_class", "userId", "status", "total", "items", "version", "confirmedAt", "updatedAt");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldApplyTheSameConfirmationOnlyOnce() {
        UUID orderId = UUID.randomUUID();
        OrderConfirmedEvent confirmed = confirmed(orderId, 1L);

        publish(OrderFacts.CONFIRMED_KEY, confirmed);
        publish(OrderFacts.CONFIRMED_KEY, confirmed);
        awaitSentinel();

        Document view = viewOf(orderId);
        assertEquals("CONFIRMED", view.getString("status"));
        assertEquals(1L, view.getLong("version"));
        assertFalse(deadLettered(orderId));
    }

    @Test
    void shouldIgnoreAStatusOlderThanTheStoredOne() {
        UUID orderId = UUID.randomUUID();
        publish(OrderFacts.CONFIRMED_KEY, confirmed(orderId, 1L));
        publish(OrderFacts.DELIVERED_KEY, new OrderDeliveredEvent(orderId, 3L, Instant.now()));
        publish(OrderFacts.SHIPPED_KEY, new OrderShippedEvent(orderId, 2L, Instant.now()));
        awaitSentinel();

        Document view = viewOf(orderId);
        assertEquals("DELIVERED", view.getString("status"));
        assertEquals(3L, view.getLong("version"));
        assertFalse(deadLettered(orderId));
    }

    @Test
    void shouldApplyAStatusThatArrivesBeforeItsConfirmation() {
        UUID orderId = UUID.randomUUID();
        publish(OrderFacts.SHIPPED_KEY, new OrderShippedEvent(orderId, 2L, Instant.now()));
        publish(OrderFacts.CONFIRMED_KEY, confirmed(orderId, 1L));

        Awaitility.await().atMost(Duration.ofSeconds(60)).until(() -> {
            Document view = viewOf(orderId);
            return view != null && "SHIPPED".equals(view.getString("status"));
        });

        assertEquals(2L, viewOf(orderId).getLong("version"));
        assertFalse(deadLettered(orderId));
    }

    @Test
    void shouldFollowTheOrderThroughEachTransition() throws Exception {
        String orderId = createOrder(createProduct("Teclado", "100.00"));
        transition(orderId, "confirm");
        transition(orderId, "ship");

        UUID id = UUID.fromString(orderId);
        Awaitility.await().atMost(Duration.ofSeconds(40)).until(() -> {
            Document view = viewOf(id);
            return view != null && "SHIPPED".equals(view.getString("status"));
        });

        assertEquals("SHIPPED", jdbcTemplate.queryForObject("SELECT status FROM orders", String.class));
        assertEquals(
                jdbcTemplate.queryForObject("SELECT version FROM orders", Long.class),
                viewOf(id).getLong("version"));
    }

    @Test
    void shouldKeepTheItemsAsTheOrderHadThemWhenConfirmed() throws Exception {
        String productId = createProduct("Teclado", "100.00");
        String orderId = createOrder(productId);

        mockMvc.perform(put("/products/{id}", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Teclado",
                                    "description": "Descricao",
                                    "price": 500.00
                                }
                                """))
                .andExpect(status().isOk());

        transition(orderId, "confirm");

        UUID id = UUID.fromString(orderId);
        awaitView(id);

        Document line = viewOf(id).getList("items", Document.class).get(0);
        assertEquals("Teclado", line.getString("productName"));
        assertThat(((Decimal128) line.get("unitPrice")).bigDecimalValue()).isEqualByComparingTo("100.00");
        assertThat(jdbcTemplate.queryForObject("SELECT unit_price FROM order_items", BigDecimal.class))
                .isEqualByComparingTo("100.00");
    }

    @Test
    void shouldStoreNoPersonalDataBeyondTheOwnerId() {
        UUID orderId = UUID.randomUUID();
        publish(OrderFacts.CONFIRMED_KEY, confirmed(orderId, 1L));
        awaitView(orderId);

        assertTrue(ALLOWED_FIELDS.containsAll(viewOf(orderId).keySet()));
    }

    @Test
    void shouldDiscardConfirmationWithoutSnapshotAndKeepTheQueueMoving() {
        UUID legacyOrderId = UUID.randomUUID();
        publish(OrderFacts.CONFIRMED_KEY, new OrderConfirmedEvent(
                legacyOrderId, UUID.randomUUID(), new BigDecimal("100.00"), null, null, Instant.now()));

        awaitSentinel();

        assertNull(viewOf(legacyOrderId));
        assertFalse(deadLettered(legacyOrderId));
    }

    private static OrderConfirmedEvent confirmed(UUID orderId, long version) {
        return new OrderConfirmedEvent(
                orderId,
                UUID.randomUUID(),
                new BigDecimal("100.00"),
                List.of(new OrderConfirmedEvent.Item(UUID.randomUUID(), "Teclado", new BigDecimal("100.00"), 1, new BigDecimal("100.00"))),
                version,
                Instant.now());
    }

    private void publish(String routingKey, Object event) {
        rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE, routingKey, event);
    }

    private void awaitSentinel() {
        UUID sentinel = UUID.randomUUID();
        publish(OrderFacts.CONFIRMED_KEY, confirmed(sentinel, 1L));
        awaitView(sentinel);
    }

    private void awaitView(UUID orderId) {
        Awaitility.await().atMost(Duration.ofSeconds(40)).until(() -> viewOf(orderId) != null);
    }

    private Document viewOf(UUID orderId) {
        return mongoTemplate.findById(orderId.toString(), Document.class, VIEWS);
    }

    private boolean deadLettered(UUID orderId) {
        boolean found = false;
        Message message;
        while ((message = rabbitTemplate.receive(RabbitMqConfig.DEAD_LETTER_QUEUE)) != null) {
            found |= new String(message.getBody()).contains(orderId.toString());
        }
        return found;
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
