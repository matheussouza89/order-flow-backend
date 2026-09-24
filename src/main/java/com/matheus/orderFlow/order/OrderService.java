package com.matheus.orderFlow.order;

import com.matheus.orderFlow.product.ProductService;
import com.matheus.orderFlow.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final ProductService productService;

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID orderId) {
        return OrderResponse.from(findOrThrow(orderId));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAll().stream()
                .map(OrderResponse::from)
                .toList();
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
        Order savedOrder = orderRepository.save(new Order(orderItems));

        log.info("Order created: id={} total={} items={}",
                savedOrder.getId(), savedOrder.getTotal(), savedOrder.getItems().size());

        return OrderResponse.from(savedOrder);
    }

    private Order findOrThrow(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException(orderId));
    }

    @Transactional
    public OrderResponse confirmOrder(UUID orderId) {
        Order order = findOrThrow(orderId);
        OrderStatus previousStatus = order.getStatus();

        order.confirm();
        orderRepository.save(order);

        log.info("Order transitioned: id={} from={} to={}",
                orderId, previousStatus, order.getStatus());

        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse shipOrder(UUID orderId) {
        Order order = findOrThrow(orderId);
        OrderStatus previousStatus = order.getStatus();

        order.ship();
        orderRepository.save(order);

        log.info("Order transitioned: id={} from={} to={}",
                orderId, previousStatus, order.getStatus());

        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse deliverOrder(UUID orderId) {
        Order order = findOrThrow(orderId);
        OrderStatus previousStatus = order.getStatus();

        order.deliver();
        orderRepository.save(order);

        log.info("Order transitioned: id={} from={} to={}",
                orderId, previousStatus, order.getStatus());

        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse cancelOrder(UUID orderId) {
        Order order = findOrThrow(orderId);
        OrderStatus previousStatus = order.getStatus();

        order.cancel();
        orderRepository.save(order);

        log.info("Order transitioned: id={} from={} to={}",
                orderId, previousStatus, order.getStatus());

        return OrderResponse.from(order);
    }
}
