package com.matheus.orderFlow.order;

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

    @GetMapping("/{id}")
    public OrderResponse getOrderById(@PathVariable UUID id) {
        return orderService.getOrder(id);
    }

    @GetMapping
    public List<OrderResponse> getAllOrders() {
        return orderService.getAllOrders();
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@RequestBody OrderDto orderDto) {
        OrderResponse created = orderService.createOrder(orderDto);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();

        return ResponseEntity.created(location).body(created);
    }
}
