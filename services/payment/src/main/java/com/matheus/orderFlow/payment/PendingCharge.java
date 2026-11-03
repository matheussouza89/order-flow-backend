package com.matheus.orderFlow.payment;

import java.math.BigDecimal;
import java.util.UUID;

record PendingCharge(UUID paymentId, String idempotencyKey, BigDecimal amount) {
}
