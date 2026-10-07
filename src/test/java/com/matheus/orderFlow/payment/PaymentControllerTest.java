package com.matheus.orderFlow.payment;

import com.matheus.orderFlow.shared.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    private PaymentResponse payment(UUID id, UUID orderId, PaymentStatus status,
                                    String gatewayReference, String reason) {
        return new PaymentResponse(
                id, orderId, new BigDecimal("350.00"), status,
                gatewayReference, reason, Instant.now(), Instant.now()
        );
    }

    @Test
    void shouldGetPaymentById() throws Exception {
        UUID id = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        when(paymentService.getPayment(id))
                .thenReturn(payment(id, orderId, PaymentStatus.APPROVED, "gw-123", null));

        mockMvc.perform(get("/payments/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.amount").value(350.00))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.gatewayReference").value("gw-123"));

        verify(paymentService).getPayment(id);
    }

    @Test
    void shouldReturnNotFoundForUnknownPayment() throws Exception {
        UUID id = UUID.randomUUID();

        when(paymentService.getPayment(id)).thenThrow(new NotFoundException(id));

        mockMvc.perform(get("/payments/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldGetPaymentByOrder() throws Exception {
        UUID id = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        when(paymentService.getPaymentByOrder(orderId))
                .thenReturn(payment(id, orderId, PaymentStatus.PENDING, null, null));

        mockMvc.perform(get("/payments/orders/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(paymentService).getPaymentByOrder(orderId);
    }

    @Test
    void shouldReturnNotFoundWhenTheOrderHasNoPayment() throws Exception {
        UUID orderId = UUID.randomUUID();

        when(paymentService.getPaymentByOrder(orderId)).thenThrow(new NotFoundException(orderId));

        mockMvc.perform(get("/payments/orders/{orderId}", orderId))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @EnumSource(PaymentStatus.class)
    void shouldExposeEveryStatus(PaymentStatus status) throws Exception {
        UUID id = UUID.randomUUID();

        when(paymentService.getPayment(id))
                .thenReturn(payment(id, UUID.randomUUID(), status, null, null));

        mockMvc.perform(get("/payments/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(status.name()));
    }

    @Test
    void shouldCarryTheFailureReasonWhenDeclined() throws Exception {
        UUID id = UUID.randomUUID();

        when(paymentService.getPayment(id))
                .thenReturn(payment(id, UUID.randomUUID(), PaymentStatus.DECLINED,
                        null, "Insufficient funds"));

        mockMvc.perform(get("/payments/{id}", id))
                .andExpect(jsonPath("$.status").value("DECLINED"))
                .andExpect(jsonPath("$.reason").value("Insufficient funds"));
    }

    @Test
    void shouldNotExposeTheOwnerOrTheIdempotencyKey() throws Exception {
        UUID id = UUID.randomUUID();

        when(paymentService.getPayment(id))
                .thenReturn(payment(id, UUID.randomUUID(), PaymentStatus.APPROVED, "gw-1", null));

        mockMvc.perform(get("/payments/{id}", id))
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.idempotencyKey").doesNotExist());
    }

    @Test
    void shouldRejectAnIdThatIsNotAUuid() throws Exception {
        mockMvc.perform(get("/payments/{id}", "nao-e-uuid"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(paymentService);
    }
}
