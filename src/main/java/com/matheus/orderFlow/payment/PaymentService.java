package com.matheus.orderFlow.payment;

import com.matheus.orderFlow.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {
    private final PaymentRepository paymentRepository;

    @Transactional
    public void chargeOrder(UUID orderId, BigDecimal amount) {
        String idempotencyKey = idempotencyKeyFor(orderId);

        if (paymentRepository.existsByIdempotencyKey(idempotencyKey)) {
            log.info("Payment already registered for order: orderId={}", orderId);
            return;
        }

        Payment payment = paymentRepository.save(new Payment(orderId, amount, idempotencyKey));

        log.info("Payment created: id={} orderId={} amount={}",
                payment.getId(), orderId, amount);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(UUID id) {
        return PaymentResponse.from(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByOrder(UUID orderId) {
        return PaymentResponse.from(
                paymentRepository.findByOrderId(orderId)
                        .orElseThrow(() -> new NotFoundException(orderId))
        );
    }

    @Transactional
    void approvePayment(UUID id, String gatewayReference) {
        Payment payment = findOrThrow(id);
        payment.approve(gatewayReference);
        paymentRepository.save(payment);

        log.info("Payment approved: id={} gatewayReference={}", id, gatewayReference);
    }

    @Transactional
    void declinePayment(UUID id, String reason) {
        Payment payment = findOrThrow(id);
        payment.decline(reason);
        paymentRepository.save(payment);

        log.info("Payment declined: id={} reason={}", id, reason);
    }

    @Transactional
    void failPayment(UUID id, String reason) {
        Payment payment = findOrThrow(id);
        payment.fail(reason);
        paymentRepository.save(payment);

        log.warn("Payment failed: id={} reason={}", id, reason);
    }

    private Payment findOrThrow(UUID id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(id));
    }

    private String idempotencyKeyFor(UUID orderId) {
        return "order-" + orderId;
    }
}
