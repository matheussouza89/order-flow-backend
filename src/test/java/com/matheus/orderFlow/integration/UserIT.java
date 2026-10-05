package com.matheus.orderFlow.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String registerJson(String name, String email, String password) {
        return """
                {
                    "name": "%s",
                    "email": "%s",
                    "password": "%s"
                }
                """.formatted(name, email, password);
    }

    private String register(String name, String email, String password) throws Exception {
        String response = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(name, email, password)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return JsonPath.read(response, "$.id");
    }

    private String storedHash(String userId) {
        return jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE id = UUID_TO_BIN(?)",
                String.class, userId);
    }

    @Test
    void shouldPersistUserWithGeneratedIdAndAuditFields() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Matheus", "matheus@example.com", "senhaSegura1")))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Matheus"))
                .andExpect(jsonPath("$.email").value("matheus@example.com"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    void shouldNeverExposeThePassword() throws Exception {
        String response = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Matheus", "matheus@example.com", "senhaSegura1")))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertFalse(response.contains("senhaSegura1"));
        assertFalse(response.contains("password"));
        assertFalse(response.contains("hash"));
    }

    @Test
    void shouldStoreThePasswordOnlyAsAHash() throws Exception {
        String userId = register("Matheus", "matheus@example.com", "senhaSegura1");

        String hash = storedHash(userId);

        assertNotNull(hash);
        assertNotEquals("senhaSegura1", hash);
        assertTrue(hash.startsWith("$2"));
    }

    @Test
    void shouldPointLocationToTheCreatedUser() throws Exception {
        String location = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Matheus", "matheus@example.com", "senhaSegura1")))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getHeader("Location");

        assertNotNull(location);

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("matheus@example.com"));
    }

    @Test
    void shouldRejectDuplicatedEmail() throws Exception {
        register("Matheus", "matheus@example.com", "senhaSegura1");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Outro", "matheus@example.com", "outraSenha1")))
                .andExpect(status().isConflict());

        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
        assertEquals(1, total);
    }

    @Test
    void shouldRejectPasswordShorterThanTheMinimum() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Matheus", "matheus@example.com", "curta")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").isNotEmpty());
    }

    @Test
    void shouldRejectInvalidEmailFormat() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Matheus", "sem-arroba", "senhaSegura1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").isNotEmpty());
    }

    @Test
    void shouldRejectNameLongerThanTheColumnLimit() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("a".repeat(151), "matheus@example.com", "senhaSegura1")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnNotFoundForUnknownUser() throws Exception {
        mockMvc.perform(get("/users/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldUpdateNameKeepingTheRest() throws Exception {
        String userId = register("Matheus", "matheus@example.com", "senhaSegura1");
        String hashBefore = storedHash(userId);

        mockMvc.perform(put("/users/{id}/name", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"name\": \"Matheus Souza\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Matheus Souza"))
                .andExpect(jsonPath("$.email").value("matheus@example.com"));

        assertEquals(hashBefore, storedHash(userId));
    }

    @Test
    void shouldUpdateEmailAndFreeTheOldOne() throws Exception {
        String userId = register("Matheus", "matheus@example.com", "senhaSegura1");

        mockMvc.perform(put("/users/{id}/email", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"email\": \"novo@example.com\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("novo@example.com"));

        register("Outro", "matheus@example.com", "senhaSegura1");
    }

    @Test
    void shouldRejectUpdatingToAnEmailAlreadyTaken() throws Exception {
        register("Matheus", "matheus@example.com", "senhaSegura1");
        String otherId = register("Outro", "outro@example.com", "senhaSegura1");

        mockMvc.perform(put("/users/{id}/email", otherId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"email\": \"matheus@example.com\" }"))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldAllowKeepingTheSameEmail() throws Exception {
        String userId = register("Matheus", "matheus@example.com", "senhaSegura1");

        mockMvc.perform(put("/users/{id}/email", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"email\": \"matheus@example.com\" }"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReplaceTheHashWhenChangingThePassword() throws Exception {
        String userId = register("Matheus", "matheus@example.com", "senhaSegura1");
        String hashBefore = storedHash(userId);

        mockMvc.perform(put("/users/{id}/password", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"password\": \"outraSenhaForte1\" }"))
                .andExpect(status().isOk());

        String hashAfter = storedHash(userId);

        assertNotEquals(hashBefore, hashAfter);
        assertNotEquals("outraSenhaForte1", hashAfter);
        assertTrue(hashAfter.startsWith("$2"));
    }

    @Test
    void shouldRejectAWeakNewPassword() throws Exception {
        String userId = register("Matheus", "matheus@example.com", "senhaSegura1");
        String hashBefore = storedHash(userId);

        mockMvc.perform(put("/users/{id}/password", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"password\": \"123\" }"))
                .andExpect(status().isBadRequest());

        assertEquals(hashBefore, storedHash(userId));
    }

    @Test
    void shouldGenerateADifferentHashForTheSamePassword() throws Exception {
        String firstId = register("Matheus", "matheus@example.com", "senhaSegura1");
        String secondId = register("Outro", "outro@example.com", "senhaSegura1");

        assertNotEquals(storedHash(firstId), storedHash(secondId));
    }
}
