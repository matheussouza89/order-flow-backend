RENAME TABLE orderflow.orders TO orders.orders,
             orderflow.order_items TO orders.order_items,
             orderflow.outbox_messages TO orders.outbox_messages;
