package com.matheus.orderFlow.notification;

import com.matheus.orderFlow.shared.web.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository notificationRepository;

    @Transactional
    public void register(UUID orderId, OrderStatus orderStatus, Long orderVersion, UUID userId, BigDecimal total) {
        if (notificationRepository.existsByOrderIdAndOrderStatus(orderId, orderStatus)) {
            return;
        }

        notificationRepository.save(new Notification(orderId, orderStatus, orderVersion, userId, total, Instant.now()));
    }

    @Transactional(readOnly = true)
    public List<Notification> due() {
        return notificationRepository.findTop50ByStateAndNextAttemptAtLessThanEqualOrderByOrderVersion(
                NotificationState.PENDING, Instant.now());
    }

    @Transactional
    public boolean supersedeIfOvertaken(UUID id) {
        Notification notification = find(id);

        if (!notificationRepository.existsByOrderIdAndStateAndOrderVersionGreaterThan(
                notification.getOrderId(), NotificationState.SENT, notification.getOrderVersion())) {
            return false;
        }

        notification.superseded();
        return true;
    }

    @Transactional
    public void recordSent(UUID id) {
        find(id).sent();
    }

    @Transactional
    public void recordFailure(UUID id, String error) {
        Notification notification = find(id);
        notification.failed(error, Instant.now());
        logIfGivenUp(notification);
    }

    @Transactional
    public void recordNoRecipient(UUID id) {
        Notification notification = find(id);
        notification.withoutRecipient(Instant.now());
        logIfGivenUp(notification);
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(NotificationState state, Pageable pageable) {
        return PageResponse.of(notificationRepository.findByState(state, pageable).map(NotificationResponse::from));
    }

    private Notification find(UUID id) {
        return notificationRepository.findById(id).orElseThrow();
    }

    private void logIfGivenUp(Notification notification) {
        if (notification.getState() != NotificationState.PENDING) {
            log.error("Notification given up: id={}, orderId={}, orderStatus={}, state={}, attempts={}, lastError={}",
                    notification.getId(), notification.getOrderId(), notification.getOrderStatus(),
                    notification.getState(), notification.getAttempts(), notification.getLastError());
        }
    }
}
