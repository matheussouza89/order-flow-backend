package com.matheus.orderFlow.sales;

import com.matheus.orderFlow.contracts.order.OrderFacts;
import com.matheus.orderFlow.contracts.order.OrderCancelledEvent;
import com.matheus.orderFlow.contracts.order.OrderConfirmedEvent;
import com.matheus.orderFlow.contracts.order.OrderDeliveredEvent;
import com.matheus.orderFlow.contracts.order.OrderShippedEvent;
import com.matheus.orderFlow.shared.messaging.RabbitMqConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.ReplaceOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@RabbitListener(queues = SalesMessagingConfig.PROJECTION_QUEUE, containerFactory = RabbitMqConfig.RESILIENT_LISTENER_FACTORY)
class OrderViewProjector {

    private final MongoTemplate mongoTemplate;
    private final RabbitTemplate rabbitTemplate;

    @RabbitHandler
    void on(OrderConfirmedEvent event) {
        if (event.items() == null || event.version() == null) {
            log.warn("Discarding confirmation without snapshot: orderId={}", event.orderId());
            return;
        }

        OrderView view = OrderView.confirmed(event);
        Query storedIsOlder = Query.query(
                Criteria.where("_id").is(view.getId()).and("version").lt(view.getVersion()));

        try {
            mongoTemplate.replace(storedIsOlder, view, ReplaceOptions.replaceOptions().upsert(), OrderView.COLLECTION);
        } catch (DuplicateKeyException alreadyApplied) {
            log.debug("Confirmation already applied or superseded: orderId={}", event.orderId());
        }
    }

    @RabbitHandler
    void on(OrderShippedEvent event, @Header(name = SalesMessagingConfig.DEFERRALS_HEADER, required = false) Integer deferrals) {
        if (!applyStatus(event.orderId(), "SHIPPED", event.version(), event.occurredAt())) {
            defer(OrderFacts.SHIPPED_KEY, event, deferrals);
        }
    }

    @RabbitHandler
    void on(OrderDeliveredEvent event, @Header(name = SalesMessagingConfig.DEFERRALS_HEADER, required = false) Integer deferrals) {
        if (!applyStatus(event.orderId(), "DELIVERED", event.version(), event.occurredAt())) {
            defer(OrderFacts.DELIVERED_KEY, event, deferrals);
        }
    }

    @RabbitHandler
    void on(OrderCancelledEvent event, @Header(name = SalesMessagingConfig.DEFERRALS_HEADER, required = false) Integer deferrals) {
        if (!applyStatus(event.orderId(), "CANCELLED", event.version(), event.occurredAt())) {
            defer(OrderFacts.CANCELLED_KEY, event, deferrals);
        }
    }

    private boolean applyStatus(UUID orderId, String status, long version, Instant occurredAt) {
        String id = orderId.toString();
        Query storedIsOlder = Query.query(Criteria.where("_id").is(id).and("version").lt(version));
        Update change = Update.update("status", status)
                .set("version", version)
                .set("updatedAt", occurredAt);

        if (mongoTemplate.updateFirst(storedIsOlder, change, OrderView.COLLECTION).getMatchedCount() == 1) {
            return true;
        }

        OrderView stored = mongoTemplate.findById(id, OrderView.class);
        if (stored == null || stored.getVersion() < version) {
            return false;
        }

        log.debug("Stale {} ignored: orderId={} version={}", status, orderId, version);
        return true;
    }

    private void defer(String routingKey, Object event, Integer deferrals) {
        int done = deferrals == null ? 0 : deferrals;

        if (done >= SalesMessagingConfig.MAX_DEFERRALS) {
            log.warn("Status still without its order view after {} deferrals, sending to DLQ: routingKey={}",
                    done, routingKey);
            rabbitTemplate.convertAndSend(RabbitMqConfig.DEAD_LETTER_EXCHANGE, "", event);
            return;
        }

        rabbitTemplate.convertAndSend(SalesMessagingConfig.WAIT_EXCHANGE, routingKey, event, message -> {
            message.getMessageProperties().setHeader(SalesMessagingConfig.DEFERRALS_HEADER, done + 1);
            return message;
        });
        log.debug("Status deferred until its order view exists: routingKey={} deferrals={}", routingKey, done + 1);
    }
}
