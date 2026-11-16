package com.matheus.orderFlow.notification;

import com.matheus.orderFlow.contact.ContactService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
class NotificationSender {
    private final NotificationService notificationService;
    private final ContactService contactService;
    private final MailSender mailSender;
    private final NotificationMessages messages;

    @Scheduled(fixedDelayString = "${orderflow.notifications.sender.poll-interval:5000}")
    void sendDue() {
        notificationService.due().forEach(this::send);
    }

    private void send(Notification notification) {
        Optional<String> email = Optional.ofNullable(notification.getUserId()).flatMap(contactService::emailOf);

        if (email.isEmpty()) {
            notificationService.recordNoRecipient(notification.getId());
            return;
        }

        try {
            mailSender.send(messages.compose(notification, email.get()));
        } catch (MailException exception) {
            log.warn("Notification not sent: id={}", notification.getId(), exception);
            notificationService.recordFailure(notification.getId(),
                    exception.getClass().getSimpleName() + ": " + exception.getMessage());
            return;
        }

        notificationService.recordSent(notification.getId());
    }
}
