package com.matheus.orderFlow.payment;

import java.math.BigDecimal;

record ChargeRequest(BigDecimal amount, String currency, String reference) {
}
