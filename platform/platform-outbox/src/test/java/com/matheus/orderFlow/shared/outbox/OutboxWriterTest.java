package com.matheus.orderFlow.shared.outbox;

import com.matheus.orderFlow.shared.messaging.FactTypes;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxWriterTest {
    private static final String TRACEPARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    record SampleHappened(String name) {
    }

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private Tracer tracer;

    @Mock
    private Propagator propagator;

    @Mock
    private Span span;

    @Mock
    private TraceContext context;

    private OutboxWriter outboxWriter;

    @BeforeEach
    void setUp() {
        outboxWriter = new OutboxWriter(outboxRepository, JsonMapper.builder().build(),
                new FactTypes(Map.of("sample.happened.v1", SampleHappened.class), Map.of()),
                tracer, propagator);
    }

    private OutboxMessage saved() {
        ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void shouldStoreTheTraceparentOfTheCurrentTrace() {
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(context);
        doAnswer(invocation -> {
            Propagator.Setter<Object> setter = invocation.getArgument(2);
            setter.set(invocation.getArgument(1), "traceparent", TRACEPARENT);
            return null;
        }).when(propagator).inject(eq(context), any(), any());

        outboxWriter.record("sample.happened", new SampleHappened("Teclado"));

        assertEquals(TRACEPARENT, saved().getTraceparent());
    }

    @Test
    void shouldStoreNoTraceparentOutsideATrace() {
        when(tracer.currentSpan()).thenReturn(null);

        outboxWriter.record("sample.happened", new SampleHappened("Teclado"));

        assertNull(saved().getTraceparent());
        verifyNoInteractions(propagator);
    }

    @Test
    void shouldStoreTheLogicalTypeIdOfTheFact() {
        outboxWriter.record("sample.happened", new SampleHappened("Teclado"));

        assertEquals("sample.happened.v1", saved().getPayloadType());
    }
}
