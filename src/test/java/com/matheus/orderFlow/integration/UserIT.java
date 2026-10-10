package com.matheus.orderFlow.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationRunner adminBootstrap;

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

    private String login(String email, String password) throws Exception {
        return mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "%s", "password": "%s" }
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @Test
    void shouldReturnABearerTokenOnLogin() throws Exception {
        register("Matheus", "matheus@example.com", "senhaSegura1");

        String response = login("matheus@example.com", "senhaSegura1");

        assertEquals("Bearer", JsonPath.read(response, "$.type"));
        assertEquals(900, (int) JsonPath.read(response, "$.expiresIn"));

        String token = JsonPath.read(response, "$.token");
        assertEquals(3, token.split("\\.").length);
    }

    @Test
    void shouldCarryTheUserIdAndRoleInTheToken() throws Exception {
        String userId = register("Matheus", "matheus@example.com", "senhaSegura1");

        String token = JsonPath.read(login("matheus@example.com", "senhaSegura1"), "$.token");

        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]));

        assertTrue(payload.contains(userId));
        assertTrue(payload.contains("USER"));
    }

    @Test
    void shouldNotCarryPersonalDataInTheToken() throws Exception {
        register("Matheus", "matheus@example.com", "senhaSegura1");

        String token = JsonPath.read(login("matheus@example.com", "senhaSegura1"), "$.token");

        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]));

        assertFalse(payload.contains("matheus@example.com"));
        assertFalse(payload.contains("Matheus"));
        assertFalse(payload.contains("senhaSegura1"));
    }

    @Test
    void shouldRejectLoginWithWrongPassword() throws Exception {
        register("Matheus", "matheus@example.com", "senhaSegura1");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "matheus@example.com", "password": "errada123" }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAnswerTheSameForUnknownEmailAndWrongPassword() throws Exception {
        register("Matheus", "matheus@example.com", "senhaSegura1");

        String wrongPassword = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "matheus@example.com", "password": "errada123" }
                                """))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String unknownEmail = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "ninguem@example.com", "password": "errada123" }
                                """))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertEquals(wrongPassword, unknownEmail);
    }

    @Test
    void shouldLoginWithTheNewPasswordAfterChangingIt() throws Exception {
        String userId = register("Matheus", "matheus@example.com", "senhaSegura1");

        mockMvc.perform(put("/users/{id}/password", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"password\": \"outraSenhaForte1\" }"))
                .andExpect(status().isOk());

        login("matheus@example.com", "outraSenhaForte1");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "matheus@example.com", "password": "senhaSegura1" }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldLetTheBootstrappedAdministratorLogInAndReachAdminRoutes() throws Exception {
        jdbcTemplate.execute("DELETE FROM users");
        adminBootstrap.run(null);

        String response = login("admin@orderflow.local", "change-me-in-production");

        String token = JsonPath.read(response, "$.token");
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]));

        assertTrue(payload.contains("ADMIN"));

        mockMvc.perform(post("/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Teclado",
                                    "description": "Mecanico",
                                    "price": 100.00
                                }
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void shouldNotCreateASecondAdministrator() throws Exception {
        jdbcTemplate.execute("DELETE FROM users");

        adminBootstrap.run(null);
        adminBootstrap.run(null);

        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE role = 'ADMIN'", Integer.class);

        assertEquals(1, total);
    }

    @Test
    void shouldGenerateADifferentHashForTheSamePassword() throws Exception {
        String firstId = register("Matheus", "matheus@example.com", "senhaSegura1");
        String secondId = register("Outro", "outro@example.com", "senhaSegura1");

        assertNotEquals(storedHash(firstId), storedHash(secondId));
    }
}
