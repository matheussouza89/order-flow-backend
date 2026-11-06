package com.matheus.orderFlow.e2e;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testcontainers.containers.Container;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class DataOwnershipE2E extends AbstractE2E {

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
}
