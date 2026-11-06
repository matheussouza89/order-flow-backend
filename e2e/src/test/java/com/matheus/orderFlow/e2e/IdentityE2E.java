package com.matheus.orderFlow.e2e;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IdentityE2E extends AbstractE2E {
    private static final String PRODUCT = """
            { "name": "Produto", "description": "Produto de teste", "price": 10.00 }
            """;

    private static String refreshBody(String refreshToken) {
        return "{ \"refreshToken\": \"%s\" }".formatted(refreshToken);
    }

    private static void redis(String... command) {
        String[] redisCli = Stream.concat(
                        Stream.of("redis-cli", "--no-auth-warning", "--user", "identity", "--pass", "identity"),
                        Stream.of(command))
                .toArray(String[]::new);

        Container.ExecResult result = runIn("redis", redisCli);
        assertEquals(0, result.getExitCode(), result.getStderr());
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Test
    void shouldRegisterLogInRefreshAndLogOutThroughTheSingleEntry() {
        Customer customer = newCustomer();

        HttpResponse<String> refreshed = post("/auth/refresh", null, refreshBody(customer.refreshToken()));
        assertEquals(200, refreshed.statusCode(), refreshed.body());
        String rotated = read(refreshed, "$.refreshToken");
        assertEquals(200, get("/orders", "Bearer " + read(refreshed, "$.token")).statusCode());

        assertEquals(401, post("/auth/refresh", null, refreshBody(customer.refreshToken())).statusCode());
        assertEquals(401, post("/auth/refresh", null, refreshBody(rotated)).statusCode());

        String anotherLogin = read(login(customer.email(), CUSTOMER_PASSWORD), "$.refreshToken");
        assertEquals(204, post("/auth/logout", null, refreshBody(anotherLogin)).statusCode());
        assertEquals(401, post("/auth/refresh", null, refreshBody(anotherLogin)).statusCode());
    }

    @Test
    void shouldRenewARefreshTokenStoredBeforeTheExtractionWithoutThePassword() {
        Customer customer = newCustomer();
        String rawToken = "emitido-antes-da-extracao-" + UUID.randomUUID();
        String key = sha256(rawToken);
        String familyId = UUID.randomUUID().toString();

        redis("HSET", "refreshToken:" + key,
                "_class", "com.matheus.orderFlow.user.RefreshToken",
                "id", key, "userId", customer.id(), "familyId", familyId, "used", "0", "ttlSeconds", "604800");
        redis("EXPIRE", "refreshToken:" + key, "604800");
        redis("SADD", "refreshToken", key);
        redis("SADD", "refreshToken:familyId:" + familyId, key);
        redis("SADD", "refreshToken:" + key + ":idx", "refreshToken:familyId:" + familyId);

        HttpResponse<String> refreshed = post("/auth/refresh", null, refreshBody(rawToken));

        assertEquals(200, refreshed.statusCode(), refreshed.body());
        assertEquals(200, get("/orders", "Bearer " + read(refreshed, "$.token")).statusCode());
        assertEquals(401, post("/auth/refresh", null, refreshBody(rawToken)).statusCode());
    }

    @Test
    void shouldKeepAcceptingAValidTokenWhileTheIdentityIsDown() {
        String admin = adminBearer();
        Customer customer = newCustomer();
        assertEquals(201, post("/products", admin, PRODUCT).statusCode());
        assertEquals(404, get("/cart", customer.bearer()).statusCode());
        assertEquals(200, get("/orders", customer.bearer()).statusCode());

        stop("identity");
        try {
            assertEquals(201, post("/products", admin, PRODUCT).statusCode());
            assertEquals(404, get("/cart", customer.bearer()).statusCode());
            assertEquals(200, get("/orders", customer.bearer()).statusCode());
        } finally {
            start("identity");
        }

        Awaitility.await()
                .atMost(Duration.ofSeconds(90))
                .pollInterval(Duration.ofSeconds(2))
                .ignoreExceptions()
                .until(() -> {
                    String signedInAgain = "Bearer " + read(login(customer.email(), CUSTOMER_PASSWORD), "$.token");
                    return post("/products", signedInAgain, PRODUCT).statusCode() == 403
                            && get("/cart", signedInAgain).statusCode() == 404
                            && get("/orders", signedInAgain).statusCode() == 200;
                });
    }

    @Test
    void shouldAnswerTheSameForAnUnknownEmailAndAWrongPassword() {
        Customer customer = newCustomer();

        HttpResponse<String> wrongPassword = login(customer.email(), "senhaErrada123");
        HttpResponse<String> unknownEmail = login("ninguem-" + UUID.randomUUID() + "@example.com", "senhaErrada123");

        assertEquals(401, wrongPassword.statusCode());
        assertEquals(401, unknownEmail.statusCode());
        assertEquals(wrongPassword.body(), unknownEmail.body());
    }
}
