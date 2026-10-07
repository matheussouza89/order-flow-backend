package com.matheus.orderFlow.payment;

import com.matheus.orderFlow.shared.exception.NotFoundException;
import com.matheus.orderFlow.shared.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final AuthenticatedUser authenticatedUser;

    @Transactional
    Optional<PendingCharge> registerPending(UUID orderId, UUID userId, BigDecimal amount) {
        String idempotencyKey = idempotencyKeyFor(orderId);

        if (paymentRepository.existsByIdempotencyKey(idempotencyKey)) {
            log.info("Payment already registered for order: orderId={}", orderId);
            return Optional.empty();
        }

        Payment payment = paymentRepository.save(new Payment(orderId, userId, amount, idempotencyKey));

        log.info("Payment created: id={} orderId={} amount={}",
                payment.getId(), orderId, amount);

        return Optional.of(new PendingCharge(payment.getId(), idempotencyKey, amount));
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(UUID id) {
        return PaymentResponse.from(requireOwnership(findOrThrow(id), id));
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByOrder(UUID orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new NotFoundException(orderId));

        return PaymentResponse.from(requireOwnership(payment, orderId));
    }

    private Payment requireOwnership(Payment payment, UUID requestedId) {
        if (!authenticatedUser.isAdmin() && !payment.belongsTo(authenticatedUser.requireId())) {
            log.warn("Payment requested by a user who does not own it: id={}", requestedId);
            throw new NotFoundException(requestedId);
        }

        return payment;
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
