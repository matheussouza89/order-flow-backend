package com.matheus.orderFlow.payment;

import com.matheus.orderFlow.shared.exception.DomainValidationException;
import com.matheus.orderFlow.shared.exception.InvalidStatusTransitionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class PaymentTest {

    private Payment pendingPayment() {
        return new Payment(UUID.randomUUID(), new BigDecimal("100.00"), "key-1");
    }

    private Payment paymentAt(PaymentStatus status) {
        Payment payment = pendingPayment();

        switch (status) {
            case APPROVED -> payment.approve("gw-1");
            case DECLINED -> payment.decline("insufficient funds");
            case FAILED -> payment.fail("gateway timeout");
            case PENDING -> {
            }
        }

        return payment;
    }

    private static Stream<PaymentStatus> statusesThatCannotTransition() {
        return EnumSet.complementOf(EnumSet.of(PaymentStatus.PENDING)).stream();
    }

    @Test
    void shouldStartAsPending() {
        Payment payment = pendingPayment();

        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        assertNull(payment.getGatewayReference());
        assertNull(payment.getReason());
        assertEquals(0, new BigDecimal("100.00").compareTo(payment.getAmount()));
    }

    @Test
    void shouldApprovePendingPayment() {
        Payment payment = pendingPayment();

        payment.approve("gw-123");

        assertEquals(PaymentStatus.APPROVED, payment.getStatus());
        assertEquals("gw-123", payment.getGatewayReference());
        assertNull(payment.getReason());
    }

    @Test
    void shouldDeclinePendingPayment() {
        Payment payment = pendingPayment();

        payment.decline("insufficient funds");

        assertEquals(PaymentStatus.DECLINED, payment.getStatus());
        assertEquals("insufficient funds", payment.getReason());
        assertNull(payment.getGatewayReference());
    }

    @Test
    void shouldFailPendingPayment() {
        Payment payment = pendingPayment();

        payment.fail("gateway timeout");

        assertEquals(PaymentStatus.FAILED, payment.getStatus());
        assertEquals("gateway timeout", payment.getReason());
    }

    @ParameterizedTest
    @MethodSource("statusesThatCannotTransition")
    void shouldNotApproveWhenPaymentIsNotPending(PaymentStatus status) {
        Payment payment = paymentAt(status);

        assertThrows(InvalidStatusTransitionException.class, () -> payment.approve("gw-2"));
    }

    @ParameterizedTest
    @MethodSource("statusesThatCannotTransition")
    void shouldNotDeclineWhenPaymentIsNotPending(PaymentStatus status) {
        Payment payment = paymentAt(status);

        assertThrows(InvalidStatusTransitionException.class, () -> payment.decline("any reason"));
    }

    @ParameterizedTest
    @MethodSource("statusesThatCannotTransition")
    void shouldNotFailWhenPaymentIsNotPending(PaymentStatus status) {
        Payment payment = paymentAt(status);

        assertThrows(InvalidStatusTransitionException.class, () -> payment.fail("any reason"));
    }

    @Test
    void shouldRejectNullOrderId() {
        assertThrows(DomainValidationException.class,
                () -> new Payment(null, new BigDecimal("100.00"), "key-1"));
    }

    @Test
    void shouldRejectNullAmount() {
        assertThrows(DomainValidationException.class,
                () -> new Payment(UUID.randomUUID(), null, "key-1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "-100.00"})
    void shouldRejectAmountThatIsNotPositive(String amount) {
        assertThrows(DomainValidationException.class,
                () -> new Payment(UUID.randomUUID(), new BigDecimal(amount), "key-1"));
    }

    @Test
    void shouldRejectNullIdempotencyKey() {
        assertThrows(DomainValidationException.class,
                () -> new Payment(UUID.randomUUID(), new BigDecimal("100.00"), null));
    }
}
