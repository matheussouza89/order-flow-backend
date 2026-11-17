package com.matheus.orderFlow.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface NotificationRepository extends JpaRepository<Notification, UUID> {

    boolean existsByOrderIdAndOrderStatus(UUID orderId, OrderStatus orderStatus);

    boolean existsByOrderIdAndStateAndOrderVersionGreaterThan(UUID orderId, NotificationState state, Long orderVersion);

    List<Notification> findTop50ByStateAndNextAttemptAtLessThanEqualOrderByOrderVersion(
            NotificationState state, Instant now);

    Page<Notification> findByState(NotificationState state, Pageable pageable);
}
