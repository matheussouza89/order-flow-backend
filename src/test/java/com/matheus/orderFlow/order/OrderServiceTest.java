package com.matheus.orderFlow.order;

import com.matheus.orderFlow.product.ProductResponse;
import com.matheus.orderFlow.shared.exception.DomainValidationException;
import com.matheus.orderFlow.shared.exception.NotFoundException;
import com.matheus.orderFlow.product.ProductService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductService productService;

    @InjectMocks
    private OrderService orderService;

    private ProductResponse catalogProduct(UUID id, String name, String price) {
        return new ProductResponse(
                id, name, "Descricao", new BigDecimal(price),
                Instant.now(), Instant.now()
        );
    }

    private void returnSavedOrder() {
        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void shouldBuildItemSnapshotFromCatalog() {
        UUID productId = UUID.randomUUID();

        when(productService.getProduct(productId))
                .thenReturn(catalogProduct(productId, "Teclado", "100.00"));
        returnSavedOrder();

        OrderDto dto = new OrderDto(List.of(new OrderItemDto(productId, 2)));

        OrderResponse result = orderService.createOrder(dto);

        assertEquals(1, result.items().size());

        OrderItemResponse item = result.items().get(0);
        assertEquals(productId, item.productId());
        assertEquals("Teclado", item.productName());
        assertEquals(0, new BigDecimal("100.00").compareTo(item.unitPrice()));
        assertEquals(2, item.quantity());
        assertEquals(0, new BigDecimal("200.00").compareTo(item.subtotal()));

        assertEquals(0, new BigDecimal("200.00").compareTo(result.total()));
        assertEquals(OrderStatus.PENDING, result.status());

        verify(productService).getProduct(productId);
    }

    @Test
    void shouldSumSubtotalsOfEveryItem() {
        UUID keyboardId = UUID.randomUUID();
        UUID mouseId = UUID.randomUUID();

        when(productService.getProduct(keyboardId))
                .thenReturn(catalogProduct(keyboardId, "Teclado", "100.00"));
        when(productService.getProduct(mouseId))
                .thenReturn(catalogProduct(mouseId, "Mouse", "50.00"));
        returnSavedOrder();

        OrderDto dto = new OrderDto(List.of(
                new OrderItemDto(keyboardId, 2),
                new OrderItemDto(mouseId, 3)
        ));

        OrderResponse result = orderService.createOrder(dto);

        assertEquals(2, result.items().size());
        assertEquals(0, new BigDecimal("350.00").compareTo(result.total()));
    }

    @Test
    void shouldThrowNotFoundWhenProductDoesNotExist() {
        UUID productId = UUID.randomUUID();

        when(productService.getProduct(productId))
                .thenThrow(new NotFoundException(productId));

        OrderDto dto = new OrderDto(List.of(new OrderItemDto(productId, 1)));

        assertThrows(NotFoundException.class, () -> orderService.createOrder(dto));

        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void shouldRejectOrderWithoutItems() {
        OrderDto dto = new OrderDto(List.of());

        assertThrows(DomainValidationException.class, () -> orderService.createOrder(dto));

        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void shouldRejectOrderWithNullItems() {
        OrderDto dto = new OrderDto(null);

        assertThrows(DomainValidationException.class, () -> orderService.createOrder(dto));

        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void shouldRejectItemWithNullQuantity() {
        UUID productId = UUID.randomUUID();

        when(productService.getProduct(productId))
                .thenReturn(catalogProduct(productId, "Teclado", "100.00"));

        OrderDto dto = new OrderDto(List.of(new OrderItemDto(productId, null)));

        assertThrows(DomainValidationException.class, () -> orderService.createOrder(dto));

        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void shouldRejectItemWithZeroQuantity() {
        UUID productId = UUID.randomUUID();

        when(productService.getProduct(productId))
                .thenReturn(catalogProduct(productId, "Teclado", "100.00"));

        OrderDto dto = new OrderDto(List.of(new OrderItemDto(productId, 0)));

        assertThrows(DomainValidationException.class, () -> orderService.createOrder(dto));

        verify(orderRepository, never()).save(any(Order.class));
    }
}
