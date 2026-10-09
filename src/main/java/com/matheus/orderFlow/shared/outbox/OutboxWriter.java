package com.matheus.orderFlow.shared.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxWriter {
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public void record(String routingKey, Object payload) {
        OutboxMessage message = new OutboxMessage(
                routingKey,
                payload.getClass().getName(),
                objectMapper.writeValueAsString(payload)
        );

        outboxRepository.save(message);

        log.debug("Outbox message recorded: routingKey={}", routingKey);
    }
}
