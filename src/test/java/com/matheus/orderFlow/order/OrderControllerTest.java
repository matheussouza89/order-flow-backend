package com.matheus.orderFlow.order;

import com.matheus.orderFlow.shared.exception.DomainValidationException;
import com.matheus.orderFlow.shared.exception.InvalidStatusTransitionException;
import com.matheus.orderFlow.shared.exception.NotFoundException;
import com.matheus.orderFlow.shared.web.PageResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
public class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    private PageResponse<OrderResponse> page(List<OrderResponse> content,
                                             int page, int size, long totalElements) {
        return PageResponse.of(new PageImpl<>(content, PageRequest.of(page, size), totalElements));
    }

    private OrderResponse orderWith(UUID productId, OrderStatus status) {
        OrderItemResponse item = new OrderItemResponse(
                UUID.randomUUID(),
                productId,
                "Teclado",
                new BigDecimal("100.00"),
                2,
                new BigDecimal("200.00")
        );

        return new OrderResponse(
                UUID.randomUUID(),
                status,
                new BigDecimal("200.00"),
                List.of(item),
                Instant.now(),
                Instant.now()
        );
    }

    private String requestBody(UUID productId) {
        return """
                {
                    "items": [
                        { "productId": "%s", "quantity": 2 }
                    ]
                }
                """.formatted(productId);
    }

    @Test
    void shouldCreateOrder() throws Exception {
        UUID productId = UUID.randomUUID();

        when(orderService.createOrder(any(OrderDto.class)))
                .thenReturn(orderWith(productId, OrderStatus.PENDING));

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(productId)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.total").value(200.00))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productId").value(productId.toString()))
                .andExpect(jsonPath("$.items[0].productName").value("Teclado"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(100.00))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].subtotal").value(200.00));

        verify(orderService).createOrder(any(OrderDto.class));
    }

    @Test
    void shouldGetOrderById() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        when(orderService.getOrder(orderId)).thenReturn(orderWith(productId, OrderStatus.PENDING));

        mockMvc.perform(get("/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.items[0].productId").value(productId.toString()));

        verify(orderService).getOrder(orderId);
    }

    @Test
    void shouldReturnNotFoundForUnknownOrder() throws Exception {
        UUID orderId = UUID.randomUUID();

        when(orderService.getOrder(orderId)).thenThrow(new NotFoundException(orderId));

        mockMvc.perform(get("/orders/{id}", orderId))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldGetAllOrders() throws Exception {
        when(orderService.getAllOrders(any(Pageable.class)))
                .thenReturn(page(
                        List.of(orderWith(UUID.randomUUID(), OrderStatus.PENDING)), 0, 20, 1));

        mockMvc.perform(get("/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].total").value(200.00))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));

        verify(orderService).getAllOrders(any(Pageable.class));
    }

    @Test
    void shouldReturnEmptyPageWhenThereAreNoOrders() throws Exception {
        when(orderService.getAllOrders(any(Pageable.class)))
                .thenReturn(page(List.of(), 0, 20, 0));

        mockMvc.perform(get("/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void shouldApplyTheDefaultPageableWhenNoParametersAreSent() throws Exception {
        when(orderService.getAllOrders(any(Pageable.class)))
                .thenReturn(page(List.of(), 0, 20, 0));

        mockMvc.perform(get("/orders")).andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(orderService).getAllOrders(captor.capture());

        Pageable pageable = captor.getValue();
        assertEquals(0, pageable.getPageNumber());
        assertEquals(20, pageable.getPageSize());
        assertEquals(Sort.by(Sort.Direction.DESC, "createdAt"), pageable.getSort());
    }

    @Test
    void shouldReturnBadRequestWhenQuantityIsInvalid() throws Exception {
        UUID productId = UUID.randomUUID();

        when(orderService.createOrder(any(OrderDto.class)))
                .thenThrow(new DomainValidationException(
                        "quantity", "Quantity must be greater than zero"));

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(productId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.quantity")
                        .value("Quantity must be greater than zero"));
    }

    @Test
    void shouldReturnNotFoundWhenProductDoesNotExist() throws Exception {
        UUID productId = UUID.randomUUID();

        when(orderService.createOrder(any(OrderDto.class)))
                .thenThrow(new NotFoundException(productId));

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(productId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnBadRequestWhenJsonIsInvalid() throws Exception {
        String invalidJson = """
                {
                    "items": [
                """;

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());

        verify(orderService, never()).createOrder(any(OrderDto.class));
    }

    @Test
    void shouldConfirmOrder() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        when(orderService.confirmOrder(orderId))
                .thenReturn(orderWith(productId, OrderStatus.CONFIRMED));

        mockMvc.perform(post("/orders/{id}/confirm", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        verify(orderService).confirmOrder(orderId);
    }

    @Test
    void shouldShipOrder() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        when(orderService.shipOrder(orderId))
                .thenReturn(orderWith(productId, OrderStatus.SHIPPED));

        mockMvc.perform(post("/orders/{id}/ship", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"));

        verify(orderService).shipOrder(orderId);
    }

    @Test
    void shouldDeliverOrder() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        when(orderService.deliverOrder(orderId))
                .thenReturn(orderWith(productId, OrderStatus.DELIVERED));

        mockMvc.perform(post("/orders/{id}/deliver", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));

        verify(orderService).deliverOrder(orderId);
    }

    @Test
    void shouldCancelOrder() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        when(orderService.cancelOrder(orderId))
                .thenReturn(orderWith(productId, OrderStatus.CANCELLED));

        mockMvc.perform(post("/orders/{id}/cancel", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(orderService).cancelOrder(orderId);
    }

    @Test
    void shouldThrowWhenOrderStatusIsInvalid() throws Exception {
        UUID orderId = UUID.randomUUID();

        when(orderService.shipOrder(orderId)).thenThrow(new InvalidStatusTransitionException(
                "Only confirmed orders can be shipped"));

        mockMvc.perform(post("/orders/{id}/ship", orderId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Only confirmed orders can be shipped"));

        verify(orderService).shipOrder(orderId);
    }
}
