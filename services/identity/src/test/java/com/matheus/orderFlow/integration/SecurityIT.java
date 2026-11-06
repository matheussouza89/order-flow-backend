package com.matheus.orderFlow.integration;

import com.jayway.jsonpath.JsonPath;
import com.matheus.orderFlow.shared.test.TestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SecurityIT extends AbstractIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    private MockMvc mockMvc;

    @BeforeEach
    void buildMockMvcWithoutDefaultToken() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    private String register(String email, String password) throws Exception {
        String response = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Fulano", "email": "%s", "password": "%s" }
                                """.formatted(email, password)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.read(response, "$.id");
    }

    private void login(String email, String password) throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "%s", "password": "%s" }
                                """.formatted(email, password)))
                .andExpect(status().isOk());
    }

    @Test
    void shouldLeaveRegistrationAndLoginOpen() throws Exception {
        register("matheus@example.com", "senhaSegura1");

        login("matheus@example.com", "senhaSegura1");
    }

    @Test
    void shouldLeaveTheDocumentationOpen() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    void shouldRequireAuthenticationOnTheAccounts() throws Exception {
        mockMvc.perform(get("/users/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    void shouldForbidTouchingAnotherUsersAccount() throws Exception {
        String victimId = register("vitima@example.com", "senhaDaVitima1");

        mockMvc.perform(get("/users/{id}", victimId)
                        .header(HttpHeaders.AUTHORIZATION, asUser()))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/users/{id}/password", victimId)
                        .header(HttpHeaders.AUTHORIZATION, asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"password\": \"senhaDoAtacante1\" }"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/users/{id}/email", victimId)
                        .header(HttpHeaders.AUTHORIZATION, asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"email\": \"atacante@example.com\" }"))
                .andExpect(status().isForbidden());

        login("vitima@example.com", "senhaDaVitima1");
    }

    @Test
    void shouldLetAUserChangeTheirOwnAccount() throws Exception {
        String ownId = register("dono@example.com", "senhaDoDono1");

        mockMvc.perform(get("/users/{id}", ownId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(UUID.fromString(ownId), "USER")))
                .andExpect(status().isOk());

        mockMvc.perform(put("/users/{id}/name", ownId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(UUID.fromString(ownId), "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"name\": \"Nome Novo\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Nome Novo"));
    }

    @Test
    void shouldLetAnAdministratorReachAnyAccount() throws Exception {
        String victimId = register("outro@example.com", "senhaDoOutro1");

        mockMvc.perform(get("/users/{id}", victimId)
                        .header(HttpHeaders.AUTHORIZATION, asAdmin()))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectATokenSignedWithAnotherKey() throws Exception {
        String ownId = register("dono@example.com", "senhaDoDono1");

        mockMvc.perform(get("/users/{id}", ownId)
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(UUID.fromString(ownId), "ADMIN")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectATamperedToken() throws Exception {
        String ownId = register("dono@example.com", "senhaDoDono1");
        String token = bearer(UUID.fromString(ownId), "USER").substring("Bearer ".length());
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1] + "." + parts[2].substring(1) + "X";

        mockMvc.perform(get("/users/{id}", ownId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }
}
