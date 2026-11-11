package com.matheus.orderFlow.e2e;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.Container;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DataOwnershipE2E extends AbstractE2E {

    private static Connection rabbitAs(String user) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(hostOf("rabbitmq", AMQP_PORT));
        factory.setPort(portOf("rabbitmq", AMQP_PORT));
        factory.setUsername(user);
        factory.setPassword(user);
        return factory.newConnection();
    }

    @ParameterizedTest(name = "{0} reading {1}")
    @CsvSource({
            "payment, orders.orders",
            "orders, payment.payments",
            "catalog, orders.orders",
            "orders, catalog.products",
            "identity, orders.orders",
            "orders, identity.users"
    })
    void shouldDenyEachServiceTheTablesOfTheOthers(String user, String table) {
        Container.ExecResult result = runIn("mysql", "mysql", "-u" + user, "-p" + user,
                "-e", "SELECT COUNT(*) FROM " + table);

        assertNotEquals(0, result.getExitCode(), result.getStdout());
        assertThat(result.getStderr()).contains("SELECT command denied");
    }

    @ParameterizedTest(name = "{0} running {1}")
    @CsvSource({
            "cart, GET refreshToken:any",
            "identity, GET cart:any",
            "cart, FLUSHALL",
            "identity, FLUSHALL"
    })
    void shouldDenyEachServiceTheRedisKeysOfTheOthers(String user, String command) {
        List<String> redisCli = new ArrayList<>(List.of("redis-cli", "--no-auth-warning", "--user", user, "--pass", user));
        redisCli.addAll(List.of(command.split(" ")));

        Container.ExecResult result = runIn("redis", redisCli.toArray(String[]::new));

        assertThat(result.getStdout() + result.getStderr()).contains("NOPERM");
    }

    @ParameterizedTest(name = "{0} consuming {1}")
    @CsvSource({
            "payment, order.views.projection",
            "sales, order.confirmed.payment",
            "orders, order.confirmed.payment",
            "orders, order.views.projection"
    })
    void shouldDenyEachServiceTheQueuesOfTheOthers(String user, String queue) throws Exception {
        try (Connection connection = rabbitAs(user)) {
            Channel channel = connection.createChannel();

            Exception refused = assertThrows(Exception.class, () -> channel.basicGet(queue, false));

            assertThat(refused).hasStackTraceContaining("ACCESS_REFUSED");
        }
    }

    @ParameterizedTest(name = "{0} publishing an order fact")
    @ValueSource(strings = {"payment", "sales"})
    void shouldLetOnlyTheOrdersServicePublishTheOrderFacts(String user) throws Exception {
        try (Connection connection = rabbitAs(user)) {
            Channel channel = connection.createChannel();
            channel.confirmSelect();

            Exception refused = assertThrows(Exception.class, () -> {
                channel.basicPublish("orderflow.events", "order.confirmed", null, "{}".getBytes(StandardCharsets.UTF_8));
                channel.waitForConfirmsOrDie(5_000);
            });

            assertThat(refused).hasStackTraceContaining("ACCESS_REFUSED");
        }
    }

    @Test
    void shouldRefuseTheSalesViewWithoutCredential() {
        Container.ExecResult result = runIn("mongo", "mongosh", "--quiet", "orderflow",
                "--eval", "db.order_views.countDocuments()");

        assertThat(result.getStdout() + result.getStderr()).contains("requires authentication");
    }
}
