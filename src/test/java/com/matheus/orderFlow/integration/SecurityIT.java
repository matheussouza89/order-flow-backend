package com.matheus.orderFlow.integration;

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

    private static final String PRODUCT_JSON = """
            {
                "name": "Teclado",
                "description": "Mecanico",
                "price": 100.00
            }
            """;

    @BeforeEach
    void buildMockMvcWithoutDefaultToken() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    @Test
    void shouldLeaveTheCatalogOpenToAnonymousVisitors() throws Exception {
        mockMvc.perform(get("/products")).andExpect(status().isOk());
        mockMvc.perform(get("/products/{id}", UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void shouldLeaveRegistrationAndLoginOpen() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Matheus",
                                    "email": "matheus@example.com",
                                    "password": "senhaSegura1"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "matheus@example.com", "password": "senhaSegura1" }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void shouldLeaveTheDocumentationOpen() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    void shouldRequireAuthenticationOnOrdersCartsAndPayments() throws Exception {
        mockMvc.perform(get("/orders")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/carts/{id}", "qualquer")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/payments/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/users/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRequireAuthenticationToChangeTheCatalog() throws Exception {
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PRODUCT_JSON))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/products/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldForbidARegularUserFromChangingTheCatalog() throws Exception {
        mockMvc.perform(post("/products")
                        .header(HttpHeaders.AUTHORIZATION, asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PRODUCT_JSON))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/products/{id}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, asUser()))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowAnAdministratorToChangeTheCatalog() throws Exception {
        mockMvc.perform(post("/products")
                        .header(HttpHeaders.AUTHORIZATION, asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PRODUCT_JSON))
                .andExpect(status().isCreated());
    }

    @Test
    void shouldAllowARegularUserOnTheirOwnResources() throws Exception {
        mockMvc.perform(get("/orders").header(HttpHeaders.AUTHORIZATION, asUser()))
                .andExpect(status().isOk());
    }

    @Test
    void shouldForbidACustomerFromShippingOrDeliveringTheirOwnOrder() throws Exception {
        String orderId = confirmedOrder();

        mockMvc.perform(post("/orders/{id}/ship", orderId)
                        .header(HttpHeaders.AUTHORIZATION, asUser()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/orders/{id}/deliver", orderId)
                        .header(HttpHeaders.AUTHORIZATION, asUser()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/orders/{id}", orderId)
                        .header(HttpHeaders.AUTHORIZATION, asUser()))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void shouldAllowACustomerToConfirmAndCancelTheirOwnOrder() throws Exception {
        String orderId = pendingOrder();

        mockMvc.perform(post("/orders/{id}/confirm", orderId)
                        .header(HttpHeaders.AUTHORIZATION, asUser()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/orders/{id}/cancel", orderId)
                        .header(HttpHeaders.AUTHORIZATION, asUser()))
                .andExpect(status().isOk());
    }

    @Test
    void shouldAllowAnAdministratorToShipAndDeliver() throws Exception {
        String orderId = confirmedOrder();

        mockMvc.perform(post("/orders/{id}/ship", orderId)
                        .header(HttpHeaders.AUTHORIZATION, asAdmin()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/orders/{id}/deliver", orderId)
                        .header(HttpHeaders.AUTHORIZATION, asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));
    }

    private String pendingOrder() throws Exception {
        String productResponse = mockMvc.perform(post("/products")
                        .header(HttpHeaders.AUTHORIZATION, asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PRODUCT_JSON))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String productId = com.jayway.jsonpath.JsonPath.read(productResponse, "$.id");

        String orderResponse = mockMvc.perform(post("/orders")
                        .header(HttpHeaders.AUTHORIZATION, asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "items": [ { "productId": "%s", "quantity": 1 } ] }
                                """.formatted(productId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return com.jayway.jsonpath.JsonPath.read(orderResponse, "$.id");
    }

    private String confirmedOrder() throws Exception {
        String orderId = pendingOrder();

        mockMvc.perform(post("/orders/{id}/confirm", orderId)
                        .header(HttpHeaders.AUTHORIZATION, asUser()))
                .andExpect(status().isOk());

        return orderId;
    }

    @Test
    void shouldRejectAMalformedToken() throws Exception {
        mockMvc.perform(get("/orders").header(HttpHeaders.AUTHORIZATION, "Bearer nao-e-um-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectATamperedToken() throws Exception {
        String token = asUser().substring("Bearer ".length());
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1] + "." + parts[2].substring(1) + "X";

        mockMvc.perform(get("/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectAMissingBearerPrefix() throws Exception {
        String token = asUser().substring("Bearer ".length());

        mockMvc.perform(get("/orders").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAnswerUnauthorizedWithTheStandardErrorBody() throws Exception {
        mockMvc.perform(get("/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    void shouldAnswerForbiddenWithTheStandardErrorBody() throws Exception {
        mockMvc.perform(post("/products")
                        .header(HttpHeaders.AUTHORIZATION, asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PRODUCT_JSON))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }
}
