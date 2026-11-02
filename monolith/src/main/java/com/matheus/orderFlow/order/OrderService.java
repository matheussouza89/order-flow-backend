package com.matheus.orderFlow.order;

import com.matheus.orderFlow.contracts.order.OrderCancelledEvent;
import com.matheus.orderFlow.contracts.order.OrderConfirmedEvent;
import com.matheus.orderFlow.contracts.order.OrderDeliveredEvent;
import com.matheus.orderFlow.contracts.order.OrderShippedEvent;
import com.matheus.orderFlow.product.ProductService;
import com.matheus.orderFlow.shared.exception.DomainValidationException;
import com.matheus.orderFlow.shared.exception.NotFoundException;
import com.matheus.orderFlow.shared.security.AuthenticatedUser;
import com.matheus.orderFlow.shared.web.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {
    private static final int MAX_EXPORT_SIZE = 500;

    private final OrderRepository orderRepository;
    private final ProductService productService;
    private final ApplicationEventPublisher events;
    private final AuthenticatedUser authenticatedUser;

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID orderId) {
        return OrderResponse.from(findOwnedOrThrow(orderId));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getAllOrders(Pageable pageable) {
        Page<Order> orders = authenticatedUser.isAdmin()
                ? orderRepository.findAll(pageable)
                : orderRepository.findByUserId(authenticatedUser.requireId(), pageable);

        return PageResponse.of(orders.map(OrderResponse::from));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderExportResponse> exportConfirmed(int page, int size) {
        if (page < 0) {
            throw new DomainValidationException("page", "Page must not be negative");
        }
        if (size < 1 || size > MAX_EXPORT_SIZE) {
            throw new DomainValidationException("size", "Size must be between 1 and " + MAX_EXPORT_SIZE);
        }

        Page<Order> orders = orderRepository.findConfirmed(PageRequest.of(page, size));
        Map<UUID, Instant> confirmedAt = confirmationTimes(orders.getContent());

        return PageResponse.of(orders.map(order -> OrderExportResponse.from(
                order, confirmedAt.getOrDefault(order.getId(), order.getCreatedAt()))));
    }

    private Map<UUID, Instant> confirmationTimes(List<Order> orders) {
        if (orders.isEmpty()) {
            return Map.of();
        }

        List<String> ids = orders.stream().map(order -> order.getId().toString()).toList();

        return orderRepository.findConfirmationTimes(ids).stream()
                .collect(Collectors.toMap(
                        row -> UUID.fromString((String) row[0]),
                        row -> Instant.parse((String) row[1]),
                        BinaryOperator.minBy(Comparator.<Instant>naturalOrder())));
    }

    @Transactional
    public OrderResponse createOrder(OrderDto dto) {
        List<OrderItemDto> itemsDto = dto.items() == null ? List.of() : dto.items();

        List<OrderItem> orderItems = itemsDto.stream()
                .map(itemDto -> {
                    var productResponse = productService.getProduct(itemDto.productId());
                    return new OrderItem(productResponse.id(), productResponse.name(), productResponse.price(), itemDto.quantity());
                })
                .toList();
        Order savedOrder = orderRepository.save(
                new Order(authenticatedUser.requireId(), orderItems));

        log.info("Order created: id={} total={} items={}",
                savedOrder.getId(), savedOrder.getTotal(), savedOrder.getItems().size());

        return OrderResponse.from(savedOrder);
    }

    private Order findOrThrow(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException(orderId));
    }

    private Order findOwnedOrThrow(UUID orderId) {
        Order order = findOrThrow(orderId);

        if (!authenticatedUser.isAdmin() && !order.belongsTo(authenticatedUser.requireId())) {
            log.warn("Order requested by a user who does not own it: id={}", orderId);
            throw new NotFoundException(orderId);
        }

        return order;
    }

    @Transactional
    public OrderResponse confirmOrder(UUID orderId) {
        Order order = findOwnedOrThrow(orderId);
        OrderStatus previousStatus = order.getStatus();

        order.confirm();
        orderRepository.saveAndFlush(order);

        events.publishEvent(
                new OrderConfirmedEvent(
                        order.getId(),
                        order.getUserId(),
                        order.getTotal(),
                        order.getItems().stream()
                                .map(item -> new OrderConfirmedEvent.Item(
                                        item.getProductId(),
                                        item.getProductName(),
                                        item.getUnitPrice(),
                                        item.getQuantity(),
                                        item.getSubtotal()))
                                .toList(),
                        order.getVersion(),
                        order.getUpdatedAt()
                )
        );

        log.info("Order transitioned: id={} from={} to={}",
                orderId, previousStatus, order.getStatus());

        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse shipOrder(UUID orderId) {
        Order order = findOrThrow(orderId);
        OrderStatus previousStatus = order.getStatus();

        order.ship();
        orderRepository.saveAndFlush(order);

        events.publishEvent(
                new OrderShippedEvent(order.getId(), order.getVersion(), order.getUpdatedAt())
        );

        log.info("Order transitioned: id={} from={} to={}",
                orderId, previousStatus, order.getStatus());

        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse deliverOrder(UUID orderId) {
        Order order = findOrThrow(orderId);
        OrderStatus previousStatus = order.getStatus();

        order.deliver();
        orderRepository.saveAndFlush(order);

        events.publishEvent(
                new OrderDeliveredEvent(order.getId(), order.getVersion(), order.getUpdatedAt())
        );

        log.info("Order transitioned: id={} from={} to={}",
                orderId, previousStatus, order.getStatus());

        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse cancelOrder(UUID orderId) {
        Order order = findOwnedOrThrow(orderId);
        OrderStatus previousStatus = order.getStatus();

        order.cancel();
        orderRepository.saveAndFlush(order);

        events.publishEvent(
                new OrderCancelledEvent(order.getId(), order.getVersion(), order.getUpdatedAt())
        );

        log.info("Order transitioned: id={} from={} to={}",
                orderId, previousStatus, order.getStatus());

        return OrderResponse.from(order);
    }
}
