package com.matheus.orderFlow.payment;

import com.matheus.orderFlow.shared.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(
        name = "Payments",
        description = "Payment query endpoints"
)
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
class PaymentController {
    private final PaymentService paymentService;

    @Operation(summary = "Get payment by id",
            description = "Payments are created automatically when an order is confirmed; there is no endpoint to create or settle one.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment found"),
            @ApiResponse(responseCode = "404", description = "Payment not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public PaymentResponse getPayment(@PathVariable UUID id) {
        return paymentService.getPayment(id);
    }

    @Operation(summary = "Get payment by order id",
            description = "Returns the payment registered for the given order.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment found"),
            @ApiResponse(responseCode = "404", description = "No payment for the given order",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/orders/{orderId}")
    public PaymentResponse getPaymentByOrder(@PathVariable UUID orderId) {
        return paymentService.getPaymentByOrder(orderId);
    }
}
