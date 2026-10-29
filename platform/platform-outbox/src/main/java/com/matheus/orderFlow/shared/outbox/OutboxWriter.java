package com.matheus.orderFlow.shared.outbox;

import com.matheus.orderFlow.shared.messaging.FactTypes;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxWriter {
    private static final String TRACEPARENT = "traceparent";

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final FactTypes factTypes;
    private final Tracer tracer;
    private final Propagator propagator;

    public void record(String routingKey, Object payload) {
        OutboxMessage message = new OutboxMessage(
                routingKey,
                factTypes.idOf(payload.getClass()),
                objectMapper.writeValueAsString(payload),
                currentTraceparent()
        );

        outboxRepository.save(message);

        log.debug("Outbox message recorded: routingKey={}", routingKey);
    }

    private String currentTraceparent() {
        Span span = tracer.currentSpan();

        if (span == null) {
            return null;
        }

        Map<String, String> carrier = new HashMap<>();
        propagator.inject(span.context(), carrier, Map::put);

        return carrier.get(TRACEPARENT);
    }
}
