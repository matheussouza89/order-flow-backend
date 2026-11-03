package com.matheus.orderFlow.e2e;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testcontainers.containers.Container;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class DataOwnershipE2E extends AbstractE2E {

    @ParameterizedTest(name = "{0} reading {1}")
    @CsvSource({
            "payment, orderflow.orders",
            "orderflow, payment.payments"
    })
    void shouldDenyEachServiceTheTablesOfTheOthers(String user, String table) {
        Container.ExecResult result = runIn("mysql", "mysql", "-u" + user, "-p" + user,
                "-e", "SELECT COUNT(*) FROM " + table);

        assertNotEquals(0, result.getExitCode(), result.getStdout());
        assertThat(result.getStderr()).contains("SELECT command denied");
    }
}
