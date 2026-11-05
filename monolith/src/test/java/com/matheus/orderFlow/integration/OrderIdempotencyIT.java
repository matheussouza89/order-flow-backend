package com.matheus.orderFlow.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderIdempotencyIT extends AbstractIntegrationTest {

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String orderJson(String productId) {
        return """
                { "items": [ { "productId": "%s", "quantity": 2 } ] }
                """.formatted(productId);
    }

    private MockHttpServletResponse createOrder(String token, String productId, String key) throws Exception {
        var request = post("/orders")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderJson(productId));

        if (key != null) {
            request.header(IDEMPOTENCY_KEY, key);
        }

        return mockMvc.perform(request)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse();
    }

    private int countOrders() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class);
    }

    @Test
    void shouldReturnTheSameOrderWhenTheKeyRepeats() throws Exception {
        String productId = catalogProduct("Teclado", "100.00");
        String key = UUID.randomUUID().toString();

        MockHttpServletResponse first = createOrder(asUser(), productId, key);
        MockHttpServletResponse second = createOrder(asUser(), productId, key);

        assertThat(second.getHeader(HttpHeaders.LOCATION)).isEqualTo(first.getHeader(HttpHeaders.LOCATION));
        assertThat(second.getContentAsString()).isEqualTo(first.getContentAsString());
        assertThat(countOrders()).isEqualTo(1);
    }

    @Test
    void shouldKeepTheKeysOfEachUserIndependent() throws Exception {
        String productId = catalogProduct("Teclado", "100.00");
        String key = UUID.randomUUID().toString();

        MockHttpServletResponse mine = createOrder(asUser(), productId, key);
        MockHttpServletResponse theirs = createOrder(asAnotherUser(), productId, key);

        assertThat(theirs.getHeader(HttpHeaders.LOCATION)).isNotEqualTo(mine.getHeader(HttpHeaders.LOCATION));
        assertThat(countOrders()).isEqualTo(2);
    }

    @Test
    void shouldCreateAnOrderOnEveryRequestWithoutTheKey() throws Exception {
        String productId = catalogProduct("Teclado", "100.00");

        createOrder(asUser(), productId, null);
        createOrder(asUser(), productId, null);

        assertThat(countOrders()).isEqualTo(2);
    }

    @Test
    void shouldRejectAKeyLongerThanSixtyFourCharacters() throws Exception {
        String productId = catalogProduct("Teclado", "100.00");

        mockMvc.perform(post("/orders")
                        .header(HttpHeaders.AUTHORIZATION, asUser())
                        .header(IDEMPOTENCY_KEY, "k".repeat(65))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson(productId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.idempotencyKey").isNotEmpty());

        assertThat(countOrders()).isZero();
    }
}
