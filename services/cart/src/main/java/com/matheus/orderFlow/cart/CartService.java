package com.matheus.orderFlow.cart;

import com.matheus.orderFlow.shared.exception.DomainValidationException;
import com.matheus.orderFlow.shared.exception.NotFoundException;
import com.matheus.orderFlow.shared.exception.ServiceUnavailableException;
import com.matheus.orderFlow.shared.security.AuthenticatedUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CartService {
    private final CartRepository cartRepository;
    private final CatalogService catalogService;
    private final OrdersService ordersService;
    private final AuthenticatedUser authenticatedUser;
    private final Duration checkoutDeadline;

    CartService(CartRepository cartRepository,
                CatalogService catalogService,
                OrdersService ordersService,
                AuthenticatedUser authenticatedUser,
                @Value("${orderflow.checkout.deadline}") Duration checkoutDeadline) {
        this.cartRepository = cartRepository;
        this.catalogService = catalogService;
        this.ordersService = ordersService;
        this.authenticatedUser = authenticatedUser;
        this.checkoutDeadline = checkoutDeadline;
    }

    public CartResponse getCart() {
        return toResponse(findOrThrow(currentCartId()));
    }

    public CartResponse addItem(CartItemDto dto) {
        String cartId = currentCartId();
        Cart cart = findOrCreate(cartId);

        catalogService.getProduct(dto.productId());
        cart.addItem(dto.productId(), dto.quantity());

        log.info("Cart item added: cartId={} productId={} quantity={}",
                cartId, dto.productId(), dto.quantity());

        return toResponse(cartRepository.save(cart));
    }

    public CartResponse setItemQuantity(UUID productId, int quantity) {
        String cartId = currentCartId();
        Cart cart = findOrThrow(cartId);

        if (quantity > 0) {
            catalogService.getProduct(productId);
        }

        cart.setItemQuantity(productId, quantity);

        log.info("Cart item quantity set: cartId={} productId={} quantity={}",
                cartId, productId, quantity);

        return toResponse(cartRepository.save(cart));
    }

    public CartResponse removeItem(UUID productId) {
        String cartId = currentCartId();
        Cart cart = findOrThrow(cartId);

        cart.removeItem(productId);

        log.info("Cart item removed: cartId={} productId={}", cartId, productId);

        return toResponse(cartRepository.save(cart));
    }

    public JsonNode checkout() {
        Instant deadline = Instant.now().plus(checkoutDeadline);
        String cartId = currentCartId();
        Cart cart = findOrThrow(cartId);

        if (cart.isEmpty()) {
            throw new DomainValidationException("items", "Cannot checkout an empty cart");
        }

        rejectUnavailableProducts(cart);
        refuseAfter(deadline, ServiceClientsConfig.CATALOG);

        String checkoutKey = cart.startCheckout();
        cartRepository.save(cart);

        JsonNode order = ordersService.createOrder(checkoutKey, OrderRequest.of(cart.getItems()));
        refuseAfter(deadline, ServiceClientsConfig.ORDERS);

        cartRepository.deleteById(cartId);

        log.info("Checkout completed: cartId={} orderId={} total={}",
                cartId, order.path("id").asString(), order.path("total"));

        return order;
    }

    private void refuseAfter(Instant deadline, String service) {
        if (Instant.now().isAfter(deadline)) {
            log.warn("Checkout deadline exceeded waiting for {}", service);
            throw new ServiceUnavailableException(service,
                    new TimeoutException("Checkout took longer than " + checkoutDeadline));
        }
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

    private String currentCartId() {
        return authenticatedUser.requireId().toString();
    }

    private Cart findOrThrow(String cartId) {
        return cartRepository.findById(cartId)
                .orElseThrow(() -> new NotFoundException(cartId));
    }

    private Optional<CatalogProduct> findAvailableProduct(UUID productId) {
        try {
            return Optional.of(catalogService.getProduct(productId));
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
