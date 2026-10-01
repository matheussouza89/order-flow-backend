package com.matheus.orderFlow.cart;

import com.matheus.orderFlow.order.OrderDto;
import com.matheus.orderFlow.order.OrderItemDto;
import com.matheus.orderFlow.order.OrderResponse;
import com.matheus.orderFlow.order.OrderService;
import com.matheus.orderFlow.product.ProductResponse;
import com.matheus.orderFlow.product.ProductService;
import com.matheus.orderFlow.shared.exception.DomainValidationException;
import com.matheus.orderFlow.shared.exception.NotFoundException;
import com.matheus.orderFlow.shared.exception.UnavailableProductException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {
    private final CartRepository cartRepository;
    private final ProductService productService;
    private final OrderService orderService;

    public CartResponse getCart(String cartId) {
        return toResponse(findOrThrow(cartId));
    }

    public CartResponse addItem(String cartId, CartItemDto dto) {
        Cart cart = findOrCreate(cartId);

        productService.getProduct(dto.productId());
        cart.addItem(dto.productId(), dto.quantity());

        log.info("Cart item added: cartId={} productId={} quantity={}",
                cartId, dto.productId(), dto.quantity());

        return toResponse(cartRepository.save(cart));
    }

    public CartResponse setItemQuantity(String cartId, UUID productId, int quantity) {
        Cart cart = findOrThrow(cartId);

        if (quantity > 0) {
            productService.getProduct(productId);
        }

        cart.setItemQuantity(productId, quantity);

        log.info("Cart item quantity set: cartId={} productId={} quantity={}",
                cartId, productId, quantity);

        return toResponse(cartRepository.save(cart));
    }

    public CartResponse removeItem(String cartId, UUID productId) {
        Cart cart = findOrThrow(cartId);

        cart.removeItem(productId);

        log.info("Cart item removed: cartId={} productId={}", cartId, productId);

        return toResponse(cartRepository.save(cart));
    }

    public OrderResponse checkout(String cartId) {
        Cart cart = findOrThrow(cartId);

        if (cart.isEmpty()) {
            throw new DomainValidationException("items", "Cannot checkout an empty cart");
        }

        rejectUnavailableProducts(cart);

        OrderDto orderDto = new OrderDto(
                cart.getItems().entrySet().stream()
                        .map(entry -> new OrderItemDto(entry.getKey(), entry.getValue()))
                        .toList()
        );

        OrderResponse order = orderService.createOrder(orderDto);

        cartRepository.deleteById(cartId);

        log.info("Checkout completed: cartId={} orderId={} total={}",
                cartId, order.id(), order.total());

        return order;
    }

    private void rejectUnavailableProducts(Cart cart) {
        List<UUID> unavailable = cart.getItems().keySet().stream()
                .filter(productId -> findAvailableProduct(productId).isEmpty())
                .toList();

        if (unavailable.isEmpty()) {
            return;
        }

        String ids = unavailable.stream()
                .map(UUID::toString)
                .collect(Collectors.joining(", "));

        throw new UnavailableProductException(
                "Cart contains products that are no longer available: " + ids);
    }

    private Cart findOrThrow(String cartId) {
        return cartRepository.findById(cartId)
                .orElseThrow(() -> new NotFoundException(cartId));
    }

    private Optional<ProductResponse> findAvailableProduct(UUID productId) {
        try {
            return Optional.of(productService.getProduct(productId));
        } catch (NotFoundException exception) {
            log.warn("Cart references a product that no longer exists: productId={}", productId);
            return Optional.empty();
        }
    }

    private Cart findOrCreate(String cartId) {
        return cartRepository.findById(cartId)
                .orElseGet(() -> new Cart(cartId));
    }

    private CartResponse toResponse(Cart cart) {
        List<CartItemResponse> items = cart.getItems().entrySet().stream()
                .map(entry -> findAvailableProduct(entry.getKey())
                        .map(product -> CartItemResponse.of(product, entry.getValue()))
                        .orElse(null))
                .filter(Objects::nonNull)
                .toList();

        BigDecimal total = items.stream()
                .map(CartItemResponse::totalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CartResponse(cart.getId(), items, total);
    }
}
