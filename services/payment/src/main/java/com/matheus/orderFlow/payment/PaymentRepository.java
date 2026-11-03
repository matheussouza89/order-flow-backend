package com.matheus.orderFlow.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface PaymentRepository extends JpaRepository<Payment, UUID> {

    boolean existsByIdempotencyKey(String idempotencyKey);

    Optional<Payment> findByOrderId(UUID orderId);
}
