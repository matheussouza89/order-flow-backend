package com.matheus.orderFlow.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
class PaymentProcessor {

    private static final int REASON_MAX_LENGTH = 300;

    private final PaymentService paymentService;
    private final PaymentGatewayClient paymentGatewayClient;

    void process(UUID orderId, UUID userId, BigDecimal amount) {
        Optional<PendingCharge> pendingCharge = paymentService.registerPending(orderId, userId, amount);

        if (pendingCharge.isEmpty()) {
            return;
        }

        settle(pendingCharge.get());
    }

    private void settle(PendingCharge charge) {
        try {
            ChargeResponse response =
                    paymentGatewayClient.charge(charge.idempotencyKey(), charge.amount());

            if (response.approved()) {
                paymentService.approvePayment(charge.paymentId(), response.id());
            } else {
                paymentService.declinePayment(charge.paymentId(), response.reason());
            }
        } catch (Exception exception) {
            log.error("Payment gateway call failed: paymentId={}", charge.paymentId(), exception);
            paymentService.failPayment(charge.paymentId(), describe(exception));
        }
    }

    private String describe(Exception exception) {
        String description = exception.getClass().getSimpleName() + ": " + exception.getMessage();

        return description.length() > REASON_MAX_LENGTH
                ? description.substring(0, REASON_MAX_LENGTH)
                : description;
    }
}
