package com.matheus.orderFlow.contact;

import com.matheus.orderFlow.contracts.user.UserEmailChangedEvent;
import com.matheus.orderFlow.contracts.user.UserRegisteredEvent;
import com.matheus.orderFlow.shared.messaging.RabbitMqConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@RabbitListener(queues = ContactMessagingConfig.USER_CONTACTS_QUEUE,
        containerFactory = RabbitMqConfig.RESILIENT_LISTENER_FACTORY)
class UserContactListener {
    private final ContactService contactService;

    @RabbitHandler
    void on(UserRegisteredEvent event) {
        contactService.record(event.userId(), event.email(), event.occurredAt());
    }

    @RabbitHandler
    void on(UserEmailChangedEvent event) {
        contactService.record(event.userId(), event.email(), event.occurredAt());
    }
}
