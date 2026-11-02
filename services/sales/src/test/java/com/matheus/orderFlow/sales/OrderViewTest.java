package com.matheus.orderFlow.sales;

import com.matheus.orderFlow.contracts.order.OrderConfirmedEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderViewTest {

    @Test
    void shouldDeriveTheTotalFromTheSubtotalsOfTheItems() {
        OrderConfirmedEvent event = new OrderConfirmedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("999.00"),
                List.of(
                        new OrderConfirmedEvent.Item(UUID.randomUUID(), "Teclado", new BigDecimal("100.00"), 2, new BigDecimal("200.00")),
                        new OrderConfirmedEvent.Item(UUID.randomUUID(), "Mouse", new BigDecimal("50.00"), 4, new BigDecimal("200.00"))
                ),
                1L,
                Instant.parse("2026-10-16T12:00:00Z"));

        OrderView view = OrderView.confirmed(event);

        assertThat(view.getTotal().bigDecimalValue()).isEqualByComparingTo("400.00");
    }
}
