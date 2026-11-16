package com.matheus.orderFlow.integration;

import com.matheus.orderFlow.contact.ContactService;
import com.matheus.orderFlow.contracts.order.OrderConfirmedEvent;
import com.matheus.orderFlow.contracts.order.OrderFacts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderConfirmedNotificationIT extends AbstractIntegrationTest {
    private static final String EMAIL = "ana@example.com";

    @Autowired
    private ContactService contactService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    private final UUID userId = UUID.randomUUID();

    private void confirm(UUID orderId) {
        publish(OrderFacts.CONFIRMED_KEY, new OrderConfirmedEvent(
                orderId, userId, new BigDecimal("150.00"), List.of(), 1L, Instant.now()));
    }

    private List<Map<String, Object>> notificationsOf(UUID orderId) {
        return jdbcTemplate.queryForList(
                "SELECT state, attempts FROM notifications WHERE order_id = UUID_TO_BIN(?)", orderId.toString());
    }

    private void awaitPendingAfterOneAttempt(UUID orderId) {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertEquals(1, notificationsOf(orderId).size());
            assertEquals("PENDING", notificationsOf(orderId).getFirst().get("state"));
            assertEquals(1, ((Number) notificationsOf(orderId).getFirst().get("attempts")).intValue());
        });
    }

    private long mailsAbout(UUID orderId) {
        return mailsTo(EMAIL).stream().filter(mail -> mail.subject().contains(orderId.toString())).count();
    }

    @Test
    void shouldEmailTheConfirmationWithTheNumberAndTheTotal() {
        contactService.record(userId, EMAIL, Instant.now());
        UUID orderId = UUID.randomUUID();

        confirm(orderId);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertEquals(1, mailsTo(EMAIL).size()));
        Mail mail = mailsTo(EMAIL).getFirst();
        assertEquals("Pedido " + orderId + " confirmado", mail.subject());
        assertThat(mail.text()).contains("Total: R$ 150,00");
        assertEquals("SENT", notificationsOf(orderId).getFirst().get("state"));
    }

    @Test
    void shouldEmailOnceWhenTheSameConfirmationArrivesTwice() {
        contactService.record(userId, EMAIL, Instant.now());
        UUID orderId = UUID.randomUUID();
        UUID nextOrder = UUID.randomUUID();

        confirm(orderId);
        confirm(orderId);
        confirm(nextOrder);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertEquals(1, mailsAbout(nextOrder)));
        assertEquals(1, mailsAbout(orderId));
        assertEquals(1, notificationsOf(orderId).size());
    }

    @Test
    void shouldKeepTryingUntilTheContactArrives() {
        UUID orderId = UUID.randomUUID();

        confirm(orderId);
        awaitPendingAfterOneAttempt(orderId);

        contactService.record(userId, EMAIL, Instant.now());
        jdbcTemplate.update("UPDATE notifications SET next_attempt_at = TIMESTAMP('2000-01-01 00:00:00')");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertEquals(1, mailsAbout(orderId)));
        assertEquals("SENT", notificationsOf(orderId).getFirst().get("state"));
    }

    @Test
    void shouldShowTheNotificationsOfAStateOnlyToAdministratorsWithoutTheEmail() throws Exception {
        UUID orderId = UUID.randomUUID();
        confirm(orderId);
        awaitPendingAfterOneAttempt(orderId);

        mockMvc.perform(get("/admin/notifications").param("state", "PENDING").header("Authorization", asUser()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/admin/notifications").param("state", "PENDING").header("Authorization", asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.content[0].attempts").value(1))
                .andExpect(jsonPath("$.content[0].email").doesNotExist())
                .andExpect(jsonPath("$.content[0].userId").doesNotExist());
    }
}
