package com.matheus.orderFlow.integration;

import com.jayway.jsonpath.JsonPath;
import com.matheus.orderFlow.contracts.order.OrderConfirmedEvent;
import com.matheus.orderFlow.contracts.order.OrderFacts;
import io.micrometer.tracing.Tracer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(OutputCaptureExtension.class)
@AutoConfigureTracing
@TestPropertySource(properties = "management.tracing.export.zipkin.enabled=false")
class TracePropagationIT extends AbstractIntegrationTest {
    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String TRACEPARENT = "00-" + TRACE_ID + "-00f067aa0ba902b7-01";
    private static final String CAPTURE_QUEUE = "test.trace.order-confirmed";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ConfirmedTraces confirmedTraces;

    private String createOrder() throws Exception {
        String product = mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Teclado", "description": "Mecanico", "price": 100.00 }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String order = mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "items": [ { "productId": "%s", "quantity": 1 } ] }
                                """.formatted(JsonPath.<String>read(product, "$.id"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.read(order, "$.id");
    }

    private static List<String> linesWith(CapturedOutput output, String text, String orderId) {
        return output.getAll().lines()
                .filter(line -> line.contains(text) && line.contains(orderId))
                .toList();
    }

    @Test
    void shouldDeliverTheConfirmationInsideTheTraceOfTheRequestThatConfirmedIt(CapturedOutput output)
            throws Exception {
        String orderId = createOrder();

        mockMvc.perform(post("/orders/{id}/confirm", orderId).header("traceparent", TRACEPARENT))
                .andExpect(status().isOk());

        String consumedTraceId = Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .until(() -> confirmedTraces.byOrder.get(UUID.fromString(orderId)), Objects::nonNull);

        assertEquals(TRACE_ID, consumedTraceId);
    }

    @Test
    void shouldWriteTheTraceIdInTheLogsOfTheRequestAndOfTheConsumer(CapturedOutput output)
            throws Exception {
        String orderId = createOrder();

        mockMvc.perform(post("/orders/{id}/confirm", orderId).header("traceparent", TRACEPARENT))
                .andExpect(status().isOk());

        Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .until(() -> !linesWith(output, "Payment created", orderId).isEmpty());

        assertThat(linesWith(output, "Order transitioned", orderId))
                .isNotEmpty()
                .allMatch(line -> line.contains(TRACE_ID));
        assertThat(linesWith(output, "Payment created", orderId))
                .allMatch(line -> line.contains(TRACE_ID));
    }

    @TestConfiguration
    static class ConfirmedTraceCaptureConfig {

        @Bean
        Queue traceCaptureQueue() {
            return QueueBuilder.durable(CAPTURE_QUEUE).build();
        }

        @Bean
        Binding traceCaptureBinding(Queue traceCaptureQueue, TopicExchange orderEventsExchange) {
            return BindingBuilder.bind(traceCaptureQueue).to(orderEventsExchange)
                    .with(OrderFacts.CONFIRMED_KEY);
        }

        @Bean
        ConfirmedTraces confirmedTraces(Tracer tracer) {
            return new ConfirmedTraces(tracer);
        }
    }

    static class ConfirmedTraces {
        private final Tracer tracer;
        private final Map<UUID, String> byOrder = new ConcurrentHashMap<>();

        ConfirmedTraces(Tracer tracer) {
            this.tracer = tracer;
        }

        @RabbitListener(queues = CAPTURE_QUEUE)
        void onConfirmed(OrderConfirmedEvent event) {
            byOrder.put(event.orderId(), tracer.currentSpan().context().traceId());
        }
    }
}
