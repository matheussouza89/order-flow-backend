package com.matheus.orderFlow.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserFactsIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String registerReturningTheUser(String email) throws Exception {
        return mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Ana", "email": "%s", "password": "password123" }
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private String register(String email) throws Exception {
        return JsonPath.read(registerReturningTheUser(email), "$.id");
    }

    private List<Map<String, Object>> outbox() {
        return jdbcTemplate.queryForList("SELECT * FROM outbox_messages ORDER BY created_at");
    }

    private static Instant instant(Object json, String path) {
        return Instant.parse(JsonPath.read(json.toString(), path));
    }

    @Test
    void shouldRecordTheRegistrationWithTheMomentTheUserWasStored() throws Exception {
        String user = registerReturningTheUser("ana@example.com");

        Map<String, Object> row = outbox().getFirst();

        assertEquals("user.registered", row.get("routing_key"));
        assertEquals("user.registered.v1", row.get("payload_type"));
        assertEquals(JsonPath.<String>read(user, "$.id"),
                JsonPath.<String>read(row.get("payload").toString(), "$.userId"));
        assertEquals("ana@example.com", JsonPath.read(row.get("payload").toString(), "$.email"));
        assertEquals(instant(user, "$.updatedAt"), instant(row.get("payload"), "$.occurredAt"));
    }

    @Test
    void shouldRecordTheNewEmailWhenItChanges() throws Exception {
        String userId = register("ana@example.com");

        String user = mockMvc.perform(put("/users/{id}/email", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "ana.nova@example.com" }
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> row = outbox().getLast();

        assertEquals("user.email-changed", row.get("routing_key"));
        assertEquals("user.email-changed.v1", row.get("payload_type"));
        assertEquals("ana.nova@example.com", JsonPath.read(row.get("payload").toString(), "$.email"));
        assertEquals(instant(user, "$.updatedAt"), instant(row.get("payload"), "$.occurredAt"));
    }

    @Test
    void shouldNotRecordAnythingWhenTheRegistrationIsRefused() throws Exception {
        register("ana@example.com");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Outra", "email": "ana@example.com", "password": "password123" }
                                """))
                .andExpect(status().isConflict());

        assertEquals(1, outbox().size());
    }

    @Test
    void shouldNotRecordAnythingWhenTheEmailChangeIsRefused() throws Exception {
        String userId = register("ana@example.com");
        register("bia@example.com");

        mockMvc.perform(put("/users/{id}/email", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "bia@example.com" }
                                """))
                .andExpect(status().isConflict());

        assertEquals(2, outbox().size());
    }

    @Test
    void shouldPublishTheRecordedFacts() throws Exception {
        register("ana@example.com");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_messages WHERE published_at IS NULL", Long.class)));
    }
}
