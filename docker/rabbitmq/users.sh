#!/bin/sh
set -eu

put() {
  curl --fail --silent --show-error --retry 30 --retry-delay 2 --retry-all-errors \
    --user "$RABBITMQ_ADMIN_USER:$RABBITMQ_ADMIN_PASSWORD" \
    --request PUT --header "content-type: application/json" \
    --data "$2" "http://rabbitmq:15672/api/$1"
}

grant() {
  put "users/$1" "{\"password\": \"$2\", \"tags\": \"\"}"
  put "permissions/%2F/$1" "{\"configure\": \"$3\", \"write\": \"$4\", \"read\": \"$5\"}"
}

publish_only() {
  put "topic-permissions/%2F/$1" "{\"exchange\": \"orderflow.events\", \"write\": \"$2\", \"read\": \".*\"}"
}

grant orders "$RABBITMQ_ORDERS_PASSWORD" \
  '^orderflow\\.events(\\.dlx|\\.dlq)?$' \
  '^orderflow\\.events(\\.dlq)?$' \
  '^orderflow\\.events\\.dlx$'

grant identity "$RABBITMQ_IDENTITY_PASSWORD" \
  '^orderflow\\.events(\\.dlx|\\.dlq)?$' \
  '^orderflow\\.events(\\.dlq)?$' \
  '^orderflow\\.events\\.dlx$'

grant notifications "$RABBITMQ_NOTIFICATIONS_PASSWORD" \
  '^(orderflow\\.events(\\.dlx|\\.dlq)?|order\\.status\\.notifications|user\\.contacts\\.notifications)$' \
  '^(orderflow\\.events\\.dlq|order\\.status\\.notifications|user\\.contacts\\.notifications)$' \
  '^(orderflow\\.events(\\.dlx)?|order\\.status\\.notifications|user\\.contacts\\.notifications)$'

grant payment "$RABBITMQ_PAYMENT_PASSWORD" \
  '^(orderflow\\.events(\\.dlx|\\.dlq)?|order\\.confirmed\\.payment)$' \
  '^(orderflow\\.events\\.dlq|order\\.confirmed\\.payment)$' \
  '^(orderflow\\.events(\\.dlx)?|order\\.confirmed\\.payment)$'

grant sales "$RABBITMQ_SALES_PASSWORD" \
  '^(orderflow\\.events(\\.dlx|\\.dlq)?|orderflow\\.views\\.(wait|return)|order\\.views\\.projection(\\.wait)?)$' \
  '^(orderflow\\.events\\.dl[xq]|orderflow\\.views\\.(wait|return)|order\\.views\\.projection(\\.wait)?)$' \
  '^(orderflow\\.events(\\.dlx)?|orderflow\\.views\\.(wait|return)|order\\.views\\.projection(\\.wait)?)$'

publish_only orders '^order\\.'
publish_only identity '^user\\.'
