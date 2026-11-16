package com.matheus.orderFlow.e2e;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class NotificationsE2E extends AbstractE2E {

    private static String order(Customer customer, String price, int quantity) {
        return placeOrder(customer, createProduct(adminBearer(), "Produto-" + UUID.randomUUID(), price), quantity);
    }

    private static List<Mail> awaitMails(Customer customer, int count, Duration atMost) {
        return Awaitility.await()
                .atMost(atMost)
                .until(() -> mailsTo(customer.email()), mails -> mails.size() >= count);
    }

    @Test
    void shouldEmailTheNumberAndTheTotalWhenTheOrderIsConfirmed() {
        Customer customer = newCustomer();
        String orderId = order(customer, "40.00", 2);

        HttpResponse<String> confirmed = post("/orders/" + orderId + "/confirm", customer.bearer(), "");
        assertEquals(200, confirmed.statusCode(), confirmed.body());

        Mail mail = awaitMails(customer, 1, Duration.ofMinutes(1)).getFirst();
        assertEquals("Pedido " + orderId + " confirmado", mail.subject());
        assertThat(mail.text()).contains("Total: R$ 80,00");
    }

    @Test
    void shouldKeepTheOrdersMovingWhileTheMailServerIsDownAndEmailWhenItReturns() {
        Customer customer = newCustomer();
        String admin = adminBearer();
        String orderId = order(customer, "25.00", 1);

        stopTheMailServer();
        try {
            assertEquals(200, post("/orders/" + orderId + "/confirm", customer.bearer(), "").statusCode());
            assertEquals(200, post("/orders/" + orderId + "/ship", admin, "").statusCode());
            assertEquals(200, post("/orders/" + orderId + "/deliver", admin, "").statusCode());
        } finally {
            startTheMailServer();
        }

        List<Mail> mails = awaitMails(customer, 1, Duration.ofMinutes(4));
        assertThat(mails).extracting(Mail::subject).contains("Pedido " + orderId + " confirmado");
    }
}
