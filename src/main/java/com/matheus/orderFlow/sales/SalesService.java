package com.matheus.orderFlow.sales;

import com.matheus.orderFlow.shared.exception.DomainValidationException;
import com.mongodb.client.MongoCollection;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class SalesService {

    private static final String BUSINESS_ZONE_NAME = "America/Sao_Paulo";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of(BUSINESS_ZONE_NAME);
    private static final long MAX_PERIOD_DAYS = 366;
    private static final int MAX_LIMIT = 100;

    private final MongoTemplate mongoTemplate;

    List<DailySalesResponse> dailySales(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new DomainValidationException("from", "Start date must not be after end date");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_PERIOD_DAYS) {
            throw new DomainValidationException("to", "Period cannot exceed " + MAX_PERIOD_DAYS + " days");
        }

        Date start = Date.from(from.atStartOfDay(BUSINESS_ZONE).toInstant());
        Date end = Date.from(to.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant());
        Document dayOfConfirmation = new Document("$dateToString", new Document("format", "%Y-%m-%d")
                .append("date", "$confirmedAt")
                .append("timezone", BUSINESS_ZONE_NAME));

        Map<LocalDate, BigDecimal> totals = new HashMap<>();
        for (Document row : collection().aggregate(List.of(
                new Document("$match", new Document("status", new Document("$ne", "CANCELLED"))
                        .append("confirmedAt", new Document("$gte", start).append("$lt", end))),
                new Document("$group", new Document("_id", dayOfConfirmation)
                        .append("total", new Document("$sum", "$total")))))) {
            totals.put(LocalDate.parse(row.getString("_id")), ((Decimal128) row.get("total")).bigDecimalValue());
        }

        return from.datesUntil(to.plusDays(1))
                .map(day -> new DailySalesResponse(day, totals.getOrDefault(day, new BigDecimal("0.00"))))
                .toList();
    }

    List<TopProductResponse> topProducts(int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new DomainValidationException("limit", "Limit must be between 1 and " + MAX_LIMIT);
        }

        List<TopProductResponse> ranking = new ArrayList<>();
        for (Document row : collection().aggregate(List.of(
                new Document("$match", new Document("status", new Document("$ne", "CANCELLED"))),
                new Document("$unwind", "$items"),
                new Document("$group", new Document("_id", "$items.productId")
                        .append("productName", new Document("$first", "$items.productName"))
                        .append("quantity", new Document("$sum", "$items.quantity"))),
                new Document("$sort", new Document("quantity", -1).append("_id", 1)),
                new Document("$limit", limit)))) {
            ranking.add(new TopProductResponse(
                    UUID.fromString(row.getString("_id")),
                    row.getString("productName"),
                    ((Number) row.get("quantity")).longValue()));
        }
        return ranking;
    }

    private MongoCollection<Document> collection() {
        return mongoTemplate.getCollection(OrderView.COLLECTION);
    }
}
