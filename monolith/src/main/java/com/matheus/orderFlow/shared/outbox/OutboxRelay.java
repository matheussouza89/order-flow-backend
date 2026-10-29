package com.matheus.orderFlow.shared.outbox;

import com.matheus.orderFlow.shared.messaging.RabbitMqConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
class OutboxRelay {
    private static final String TYPE_HEADER = "__TypeId__";

    private final OutboxRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;

    @Scheduled(fixedDelayString = "${orderflow.outbox.poll-interval:1000}")
    @Transactional
    public void publishPending() {
        List<OutboxMessage> pending =
                outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();

        if (pending.isEmpty()) {
            return;
        }

        for (OutboxMessage message : pending) {
            publish(message);
        }
    }

    private void publish(OutboxMessage outboxMessage) {
        try {
            rabbitTemplate.send(RabbitMqConfig.EXCHANGE, outboxMessage.getRoutingKey(),
                    toAmqpMessage(outboxMessage));

            outboxMessage.markPublished();

            log.info("Outbox message published: id={} routingKey={}",
                    outboxMessage.getId(), outboxMessage.getRoutingKey());
        } catch (Exception exception) {
            outboxMessage.markFailed(exception.getMessage());

            log.error("Failed to publish outbox message: id={} attempts={}",
                    outboxMessage.getId(), outboxMessage.getAttempts(), exception);
        }
    }

    private Message toAmqpMessage(OutboxMessage outboxMessage) {
        return MessageBuilder
                .withBody(outboxMessage.getPayload().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setHeader(TYPE_HEADER, outboxMessage.getPayloadType())
                .build();
    }
}
