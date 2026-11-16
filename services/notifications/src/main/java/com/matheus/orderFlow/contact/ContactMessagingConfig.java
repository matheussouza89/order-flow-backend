package com.matheus.orderFlow.contact;

import com.matheus.orderFlow.contracts.user.UserFacts;
import com.matheus.orderFlow.shared.messaging.RabbitMqConfig;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ContactMessagingConfig {
    static final String USER_CONTACTS_QUEUE = "user.contacts.notifications";

    @Bean
    Queue userContactsQueue() {
        return QueueBuilder.durable(USER_CONTACTS_QUEUE)
                .deadLetterExchange(RabbitMqConfig.DEAD_LETTER_EXCHANGE)
                .build();
    }

    @Bean
    Declarables userContactsBindings(Queue userContactsQueue, TopicExchange orderEventsExchange) {
        return new Declarables(
                BindingBuilder.bind(userContactsQueue).to(orderEventsExchange).with(UserFacts.REGISTERED_KEY),
                BindingBuilder.bind(userContactsQueue).to(orderEventsExchange).with(UserFacts.EMAIL_CHANGED_KEY)
        );
    }
}
