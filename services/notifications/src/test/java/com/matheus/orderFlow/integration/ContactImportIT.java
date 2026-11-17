package com.matheus.orderFlow.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.matheus.orderFlow.contact.ContactService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ContactImportIT extends AbstractIntegrationTest {
    private static final WireMockServer IDENTITY = new WireMockServer(options().dynamicPort());
    private static final String EXPORT = "/internal/users/export";
    private static final String IMPORT = "/admin/notifications/contacts/import";
    private static final Instant REGISTERED_AT = Instant.parse("2026-11-16T10:00:00Z");

    static {
        IDENTITY.start();
    }

    @DynamicPropertySource
    static void exportFromTheFakeIdentityService(DynamicPropertyRegistry registry) {
        registry.add("spring.http.serviceclient.identity.base-url", IDENTITY::baseUrl);
    }

    @AfterAll
    static void stopTheFakeIdentityService() {
        IDENTITY.stop();
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ContactService contactService;

    private final UUID ana = UUID.randomUUID();
    private final UUID bia = UUID.randomUUID();

    @BeforeEach
    void resetTheFakeIdentityService() {
        IDENTITY.resetAll();
    }

    private static String user(UUID userId, String email, Instant updatedAt) {
        return """
                { "userId": "%s", "email": "%s", "updatedAt": "%s" }""".formatted(userId, email, updatedAt);
    }

    private static void exportPage(int page, int totalPages, String... users) {
        IDENTITY.stubFor(get(urlPathEqualTo(EXPORT))
                .withQueryParam("page", equalTo(String.valueOf(page)))
                .withQueryParam("size", equalTo("100"))
                .willReturn(okJson("""
                        { "content": [%s], "page": %d, "size": 100, "totalElements": %d, "totalPages": %d }
                        """.formatted(String.join(",", users), page, users.length, totalPages))));
    }

    @Test
    void shouldCopyTheEmailOfEveryUserPageByPageOnBehalfOfTheAdministrator() throws Exception {
        exportPage(0, 2, user(ana, "ana@example.com", REGISTERED_AT));
        exportPage(1, 2, user(bia, "bia@example.com", REGISTERED_AT));
        String admin = asAdmin();

        mockMvc.perform(post(IMPORT).header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(2));

        assertEquals(Optional.of("ana@example.com"), contactService.emailOf(ana));
        assertEquals(Optional.of("bia@example.com"), contactService.emailOf(bia));
        IDENTITY.verify(2, getRequestedFor(urlPathEqualTo(EXPORT)).withHeader("Authorization", equalTo(admin)));
    }

    @Test
    void shouldImportNothingTheSecondTime() throws Exception {
        exportPage(0, 1, user(ana, "ana@example.com", REGISTERED_AT));

        mockMvc.perform(post(IMPORT).header("Authorization", asAdmin())).andExpect(jsonPath("$.imported").value(1));

        mockMvc.perform(post(IMPORT).header("Authorization", asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(0));
    }

    @Test
    void shouldNotOverwriteANewerEmailThatArrivedByFact() throws Exception {
        contactService.record(ana, "ana.nova@example.com", REGISTERED_AT.plusSeconds(60));
        exportPage(0, 1, user(ana, "ana@example.com", REGISTERED_AT), user(bia, "bia@example.com", REGISTERED_AT));

        mockMvc.perform(post(IMPORT).header("Authorization", asAdmin()))
                .andExpect(jsonPath("$.imported").value(1));

        assertEquals(Optional.of("ana.nova@example.com"), contactService.emailOf(ana));
    }

    @Test
    void shouldAnswerServiceUnavailableAndKeepWhatWasCopiedWhenTheIdentityServiceFails() throws Exception {
        exportPage(0, 2, user(ana, "ana@example.com", REGISTERED_AT));
        IDENTITY.stubFor(get(urlPathEqualTo(EXPORT)).withQueryParam("page", equalTo("1")).willReturn(serverError()));

        mockMvc.perform(post(IMPORT).header("Authorization", asAdmin()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));

        assertEquals(Optional.of("ana@example.com"), contactService.emailOf(ana));
    }

    @Test
    void shouldForbidRegularUsers() throws Exception {
        mockMvc.perform(post(IMPORT).header("Authorization", asUser()))
                .andExpect(status().isForbidden());

        IDENTITY.verify(0, getRequestedFor(urlPathEqualTo(EXPORT)));
    }
}
