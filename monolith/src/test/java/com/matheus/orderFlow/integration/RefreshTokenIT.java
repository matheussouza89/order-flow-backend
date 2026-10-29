package com.matheus.orderFlow.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RefreshTokenIT extends AbstractIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    private MockMvc mockMvc;

    private static final String EMAIL = "matheus@example.com";
    private static final String PASSWORD = "senhaSegura1";

    @BeforeEach
    void buildMockMvcWithoutDefaultToken() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(springSecurityFilterChain)
                .build();

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Matheus", "email": "%s", "password": "%s" }
                                """.formatted(EMAIL, PASSWORD)))
                .andExpect(status().isCreated());
    }

    private String login() throws Exception {
        return mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "%s", "password": "%s" }
                                """.formatted(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String refreshRequest(String refreshToken) throws Exception {
        return """
                { "refreshToken": "%s" }
                """.formatted(refreshToken);
    }

    @Test
    void shouldReturnBothTokensOnLogin() throws Exception {
        String response = login();

        assertNotNull(JsonPath.read(response, "$.token"));
        assertNotNull(JsonPath.read(response, "$.refreshToken"));
        assertEquals(900, (int) JsonPath.read(response, "$.expiresIn"));
    }

    @Test
    void shouldIssueAnOpaqueRefreshToken() throws Exception {
        String refreshToken = JsonPath.read(login(), "$.refreshToken");

        assertEquals(1, refreshToken.split("\\.").length);
        assertFalse(refreshToken.startsWith("ey"));
    }

    @Test
    void shouldExchangeTheRefreshTokenForAWorkingAccessToken() throws Exception {
        String refreshToken = JsonPath.read(login(), "$.refreshToken");

        String refreshed = mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshRequest(refreshToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String accessToken = JsonPath.read(refreshed, "$.token");

        mockMvc.perform(get("/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRotateTheRefreshTokenOnEveryUse() throws Exception {
        String first = JsonPath.read(login(), "$.refreshToken");

        String refreshed = mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshRequest(first)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String second = JsonPath.read(refreshed, "$.refreshToken");

        assertNotEquals(first, second);

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshRequest(second)))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRevokeTheWholeFamilyWhenARotatedTokenIsReused() throws Exception {
        String stolen = JsonPath.read(login(), "$.refreshToken");

        String refreshed = mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshRequest(stolen)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String legitimate = JsonPath.read(refreshed, "$.refreshToken");

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshRequest(stolen)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshRequest(legitimate)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectAnUnknownRefreshToken() throws Exception {
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshRequest("nao-existe")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldStopTheRefreshTokenFromWorkingAfterLogout() throws Exception {
        String refreshToken = JsonPath.read(login(), "$.refreshToken");

        mockMvc.perform(post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshRequest(refreshToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshRequest(refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldKeepSeparateLoginsIndependent() throws Exception {
        String firstLogin = JsonPath.read(login(), "$.refreshToken");
        String secondLogin = JsonPath.read(login(), "$.refreshToken");

        mockMvc.perform(post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshRequest(firstLogin)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshRequest(secondLogin)))
                .andExpect(status().isOk());
    }

    @Test
    void shouldNotStoreTheRefreshTokenAsIs() throws Exception {
        String refreshToken = JsonPath.read(login(), "$.refreshToken");

        assertFalse(redisContains(refreshToken));
    }

    @Test
    void shouldCarryTheCurrentRoleOnRefresh() throws Exception {
        String refreshToken = JsonPath.read(login(), "$.refreshToken");

        String refreshed = mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshRequest(refreshToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String accessToken = JsonPath.read(refreshed, "$.token");
        String payload = new String(java.util.Base64.getUrlDecoder()
                .decode(accessToken.split("\\.")[1]));

        assertTrue(payload.contains("USER"));
    }
}
