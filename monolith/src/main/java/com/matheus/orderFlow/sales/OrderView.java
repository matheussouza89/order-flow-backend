package com.matheus.orderFlow.sales;

import com.matheus.orderFlow.contracts.order.OrderConfirmedEvent;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.bson.types.Decimal128;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Document(collection = OrderView.COLLECTION)
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
class OrderView {

    static final String COLLECTION = "order_views";

    @Id
    private String id;
    private String userId;
    private String status;
    private Decimal128 total;
    private List<Line> items;
    private long version;
    private Instant confirmedAt;
    private Instant updatedAt;

    protected OrderView() {
    }

    static OrderView confirmed(OrderConfirmedEvent event) {
        OrderView view = new OrderView();
        view.id = event.orderId().toString();
        view.userId = event.userId().toString();
        view.status = "CONFIRMED";
        view.items = event.items().stream().map(Line::from).toList();
        view.total = new Decimal128(event.items().stream()
                .map(OrderConfirmedEvent.Item::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        view.version = event.version();
        view.confirmedAt = event.occurredAt();
        view.updatedAt = event.occurredAt();
        return view;
    }

    @Getter
    @Builder
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    static class Line {

        private String productId;
        private String productName;
        private Decimal128 unitPrice;
        private int quantity;
        private Decimal128 subtotal;

        protected Line() {
        }

        static Line from(OrderConfirmedEvent.Item item) {
            Line line = new Line();
            line.productId = item.productId().toString();
            line.productName = item.productName();
            line.unitPrice = new Decimal128(item.unitPrice());
            line.quantity = item.quantity();
            line.subtotal = new Decimal128(item.subtotal());
            return line;
        }
    }
}
