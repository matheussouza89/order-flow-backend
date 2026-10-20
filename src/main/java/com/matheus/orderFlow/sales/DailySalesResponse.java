package com.matheus.orderFlow.sales;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailySalesResponse(LocalDate date, BigDecimal total) {
}
