package com.matheus.orderFlow.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

interface OrderRepository extends JpaRepository<Order, UUID> {
    String CONFIRMED_ORDERS = """
            FROM orders o
            WHERE o.status IN ('CONFIRMED', 'SHIPPED', 'DELIVERED')
               OR (o.status = 'CANCELLED' AND o.id IN (
                    SELECT UUID_TO_BIN(JSON_UNQUOTE(JSON_EXTRACT(m.payload, '$.orderId')))
                    FROM outbox_messages m
                    WHERE m.routing_key = 'order.confirmed'))
            """;

    Page<Order> findByUserId(UUID userId, Pageable pageable);

    @Query(value = "SELECT o.* " + CONFIRMED_ORDERS + "ORDER BY o.created_at, o.id",
            countQuery = "SELECT COUNT(*) " + CONFIRMED_ORDERS,
            nativeQuery = true)
    Page<Order> findConfirmed(Pageable pageable);

    @Query(value = """
            SELECT JSON_UNQUOTE(JSON_EXTRACT(payload, '$.orderId')),
                   JSON_UNQUOTE(JSON_EXTRACT(payload, '$.occurredAt'))
            FROM outbox_messages
            WHERE routing_key = 'order.confirmed'
              AND JSON_UNQUOTE(JSON_EXTRACT(payload, '$.orderId')) IN (:orderIds)
            """, nativeQuery = true)
    List<Object[]> findConfirmationTimes(Collection<String> orderIds);
}
