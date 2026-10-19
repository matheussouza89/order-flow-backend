package com.matheus.orderFlow.integration;

import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SalesReportIT extends AbstractIntegrationTest {

    private static final String DAILY = "/admin/sales/daily";
    private static final String TOP = "/admin/sales/top-products";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Test
    void shouldReturnOneEntryPerDayWithZeroForDaysWithoutSales() throws Exception {
        seedOrder("SHIPPED", Instant.parse("2026-10-10T15:00:00Z"), "100.00", line("Teclado", "100.00", 1));
        seedOrder("DELIVERED", Instant.parse("2026-10-12T15:00:00Z"), "30.00", line("Mouse", "30.00", 1));
        seedOrder("CONFIRMED", Instant.parse("2026-10-12T16:00:00Z"), "50.00", line("Monitor", "50.00", 1));

        mockMvc.perform(get(DAILY).param("from", "2026-10-10").param("to", "2026-10-12")
                        .header("Authorization", asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].date").value("2026-10-10"))
                .andExpect(jsonPath("$[0].total").value(100.00))
                .andExpect(jsonPath("$[1].total").value(0.00))
                .andExpect(jsonPath("$[2].total").value(80.00));
    }

    @Test
    void shouldGroupOrdersByTheBrazilianDayOfConfirmation() throws Exception {
        seedOrder("CONFIRMED", Instant.parse("2026-10-11T01:30:00Z"), "100.00", line("Teclado", "100.00", 1));

        mockMvc.perform(get(DAILY).param("from", "2026-10-10").param("to", "2026-10-10")
                        .header("Authorization", asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].total").value(100.00));
    }

    @Test
    void shouldLeaveCancelledOrdersOutOfTheTotals() throws Exception {
        seedOrder("CANCELLED", Instant.parse("2026-10-10T15:00:00Z"), "999.00", line("Monitor", "999.00", 1));
        seedOrder("CONFIRMED", Instant.parse("2026-10-10T16:00:00Z"), "100.00", line("Teclado", "100.00", 1));

        mockMvc.perform(get(DAILY).param("from", "2026-10-10").param("to", "2026-10-10")
                        .header("Authorization", asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].total").value(100.00));
    }

    @Test
    void shouldRejectAPeriodThatEndsBeforeItStarts() throws Exception {
        mockMvc.perform(get(DAILY).param("from", "2026-10-12").param("to", "2026-10-10")
                        .header("Authorization", asAdmin()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectARequestWithoutTheStartDate() throws Exception {
        mockMvc.perform(get(DAILY).param("to", "2026-10-10")
                        .header("Authorization", asAdmin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.from").isNotEmpty());
    }

    @Test
    void shouldRankProductsByQuantitySoldLeavingCancelledOrdersOut() throws Exception {
        seedOrder("CONFIRMED", Instant.parse("2026-10-10T15:00:00Z"), "200.00", line("Teclado", "100.00", 2));
        seedOrder("SHIPPED", Instant.parse("2026-10-11T15:00:00Z"), "100.00", line("Teclado", "100.00", 1));
        seedOrder("DELIVERED", Instant.parse("2026-10-12T15:00:00Z"), "250.00", line("Mouse", "50.00", 5));
        seedOrder("CANCELLED", Instant.parse("2026-10-12T16:00:00Z"), "1000.00", line("Monitor", "500.00", 2));

        mockMvc.perform(get(TOP).header("Authorization", asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].productName").value("Mouse"))
                .andExpect(jsonPath("$[0].quantity").value(5))
                .andExpect(jsonPath("$[1].productName").value("Teclado"))
                .andExpect(jsonPath("$[1].quantity").value(3));
    }

    @Test
    void shouldLimitTheRanking() throws Exception {
        seedOrder("CONFIRMED", Instant.parse("2026-10-10T15:00:00Z"), "200.00", line("Teclado", "100.00", 2));
        seedOrder("DELIVERED", Instant.parse("2026-10-12T15:00:00Z"), "250.00", line("Mouse", "50.00", 5));

        mockMvc.perform(get(TOP).param("limit", "1").header("Authorization", asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].productName").value("Mouse"));
    }

    @Test
    void shouldRejectALimitOutsideOneToOneHundred() throws Exception {
        mockMvc.perform(get(TOP).param("limit", "0").header("Authorization", asAdmin()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get(TOP).param("limit", "101").header("Authorization", asAdmin()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldForbidRegularUsersFromTheSalesReports() throws Exception {
        mockMvc.perform(get(DAILY).param("from", "2026-10-10").param("to", "2026-10-10")
                        .header("Authorization", asUser()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(TOP).header("Authorization", asUser()))
                .andExpect(status().isForbidden());
    }

    private void seedOrder(String status, Instant confirmedAt, String total, Document... lines) {
        mongoTemplate.getCollection("order_views").insertOne(new Document("_id", UUID.randomUUID().toString())
                .append("userId", UUID.randomUUID().toString())
                .append("status", status)
                .append("total", new Decimal128(new BigDecimal(total)))
                .append("items", List.of(lines))
                .append("version", 1L)
                .append("confirmedAt", Date.from(confirmedAt))
                .append("updatedAt", Date.from(confirmedAt)));
    }

    private static Document line(String productName, String unitPrice, int quantity) {
        BigDecimal price = new BigDecimal(unitPrice);
        return new Document("productId", UUID.nameUUIDFromBytes(productName.getBytes()).toString())
                .append("productName", productName)
                .append("unitPrice", new Decimal128(price))
                .append("quantity", quantity)
                .append("subtotal", new Decimal128(price.multiply(BigDecimal.valueOf(quantity))));
    }
}
