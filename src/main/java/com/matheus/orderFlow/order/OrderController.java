package com.matheus.orderFlow.order;

import com.matheus.orderFlow.shared.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@Tag(
        name = "Orders",
        description = "Order management endpoints"
)
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
class OrderController {
    private final OrderService orderService;

    @Operation(summary = "Get order by ID",
            description = "Returns an order with its items, each carrying the product name and price frozen at purchase time.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved order"),
            @ApiResponse(responseCode = "404", description = "Order not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public OrderResponse getOrderById(@PathVariable UUID id) {
        return orderService.getOrder(id);
    }

    @Operation(summary = "Get all orders", description = "Returns a list of all orders in the system.")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved list of orders")
    @GetMapping
    public List<OrderResponse> getAllOrders() {
        return orderService.getAllOrders();
    }

    @Operation(summary = "Create order",
            description = """
                    Creates an order from catalog products. The client sends only product
                    identifiers and quantities; name, unit price, subtotal and total are
                    taken from the catalog and frozen. The order starts as PENDING.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Order created successfully",
                    headers = @Header(name = "Location", description = "URI of the created order",
                            schema = @Schema(type = "string"))),
            @ApiResponse(responseCode = "400", description = "Order has no items, or an item has an invalid quantity",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "One of the products does not exist",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@RequestBody OrderDto orderDto) {
        OrderResponse created = orderService.createOrder(orderDto);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();

        return ResponseEntity.created(location).body(created);
    }

    @Operation(summary = "Confirm order", description = "Moves the order from PENDING to CONFIRMED.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order confirmed"),
            @ApiResponse(responseCode = "404", description = "Order not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Order is not PENDING",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{id}/confirm")
    public OrderResponse confirmOrder(@PathVariable UUID id) {
        return orderService.confirmOrder(id);
    }

    @Operation(summary = "Ship order", description = "Moves the order from CONFIRMED to SHIPPED.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order shipped"),
            @ApiResponse(responseCode = "404", description = "Order not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Order is not CONFIRMED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{id}/ship")
    public OrderResponse shipOrder(@PathVariable UUID id) {
        return orderService.shipOrder(id);
    }

    @Operation(summary = "Deliver order", description = "Moves the order from SHIPPED to DELIVERED.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order delivered"),
            @ApiResponse(responseCode = "404", description = "Order not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Order is not SHIPPED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{id}/deliver")
    public OrderResponse deliverOrder(@PathVariable UUID id) {
        return orderService.deliverOrder(id);
    }

    @Operation(summary = "Cancel order",
            description = "Cancels an order that has not shipped yet. Allowed from PENDING or CONFIRMED.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order cancelled"),
            @ApiResponse(responseCode = "404", description = "Order not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Order has already shipped or finished",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{id}/cancel")
    public OrderResponse cancelOrder(@PathVariable UUID id) {
        return orderService.cancelOrder(id);
    }
}
