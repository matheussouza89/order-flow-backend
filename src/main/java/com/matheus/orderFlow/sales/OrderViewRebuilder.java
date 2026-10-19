package com.matheus.orderFlow.sales;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.Decimal128;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.ReplaceOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderViewRebuilder {

    private static final String ORDERS = """
            SELECT BIN_TO_UUID(id) AS id,
                   BIN_TO_UUID(user_id) AS user_id,
                   status,
                   total,
                   version,
                   UNIX_TIMESTAMP(created_at) AS created_epoch,
                   UNIX_TIMESTAMP(updated_at) AS updated_epoch
            FROM orders
            """;

    private static final String ITEMS = """
            SELECT BIN_TO_UUID(order_id) AS order_id,
                   BIN_TO_UUID(product_id) AS product_id,
                   product_name,
                   unit_price,
                   quantity,
                   subtotal
            FROM order_items
            """;

    private static final String CONFIRMATIONS = """
            SELECT JSON_UNQUOTE(JSON_EXTRACT(payload, '$.orderId')) AS order_id,
                   JSON_UNQUOTE(JSON_EXTRACT(payload, '$.occurredAt')) AS occurred_at
            FROM outbox_messages
            WHERE routing_key = 'order.confirmed'
            """;

    private final JdbcTemplate jdbcTemplate;
    private final MongoTemplate mongoTemplate;

    public int rebuild() {
        Map<String, Instant> confirmations = confirmations();
        Map<String, List<OrderView.Line>> lines = lines();
        int written = 0;

        for (Map<String, Object> row : jdbcTemplate.queryForList(ORDERS)) {
            String id = (String) row.get("id");
            String status = (String) row.get("status");
            Instant confirmedAt = confirmations.get(id);

            if (!projected(status, confirmedAt)) {
                continue;
            }

            OrderView view = OrderView.builder()
                    .id(id)
                    .userId((String) row.get("user_id"))
                    .status(status)
                    .total(new Decimal128((BigDecimal) row.get("total")))
                    .items(lines.getOrDefault(id, List.of()))
                    .version(((Number) row.get("version")).longValue())
                    .confirmedAt(confirmedAt != null ? confirmedAt : epoch(row.get("created_epoch")))
                    .updatedAt(epoch(row.get("updated_epoch")))
                    .build();

            if (write(view)) {
                written++;
            }
        }

        log.info("Order views rebuilt from MySQL: {} documents", written);
        return written;
    }

    private static boolean projected(String status, Instant confirmedAt) {
        return switch (status) {
            case "CONFIRMED", "SHIPPED", "DELIVERED" -> true;
            case "CANCELLED" -> confirmedAt != null;
            default -> false;
        };
    }

    private boolean write(OrderView view) {
        Query storedIsOlder = Query.query(
                Criteria.where("_id").is(view.getId()).and("version").lt(view.getVersion()));

        try {
            mongoTemplate.replace(storedIsOlder, view, ReplaceOptions.replaceOptions().upsert(), OrderView.COLLECTION);
            return true;
        } catch (DuplicateKeyException newerAlreadyProjected) {
            return false;
        }
    }

    private Map<String, Instant> confirmations() {
        Map<String, Instant> earliest = new HashMap<>();
        jdbcTemplate.query(CONFIRMATIONS, rs -> {
            earliest.merge(rs.getString("order_id"), Instant.parse(rs.getString("occurred_at")),
                    (a, b) -> a.isBefore(b) ? a : b);
        });
        return earliest;
    }

    private Map<String, List<OrderView.Line>> lines() {
        Map<String, List<OrderView.Line>> lines = new HashMap<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList(ITEMS)) {
            lines.computeIfAbsent((String) row.get("order_id"), key -> new ArrayList<>()).add(
                    OrderView.Line.builder()
                            .productId((String) row.get("product_id"))
                            .productName((String) row.get("product_name"))
                            .unitPrice(new Decimal128((BigDecimal) row.get("unit_price")))
                            .quantity(((Number) row.get("quantity")).intValue())
                            .subtotal(new Decimal128((BigDecimal) row.get("subtotal")))
                            .build());
        }
        return lines;
    }

    private static Instant epoch(Object seconds) {
        BigDecimal value = new BigDecimal(seconds.toString());
        return Instant.ofEpochSecond(
                value.longValue(),
                value.remainder(BigDecimal.ONE).movePointRight(9).longValue());
    }
}
