package com.matheus.orderFlow.integration;

import com.jayway.jsonpath.JsonPath;
import com.matheus.orderFlow.TestcontainersConfiguration;
import com.matheus.orderFlow.shared.messaging.RabbitMqConfig;
import com.matheus.orderFlow.shared.test.TestJwtDecoderConfiguration;
import com.matheus.orderFlow.shared.test.TestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Import({TestcontainersConfiguration.class, TestJwtDecoderConfiguration.class})
@SpringBootTest(properties = "orderflow.notifications.sender.poll-interval=200")
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {
    private static final int SMTP_PORT = 1025;
    private static final int API_PORT = 8025;
    private static final GenericContainer<?> MAILPIT =
            new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.32.0"))
                    .withExposedPorts(SMTP_PORT, API_PORT)
                    .waitingFor(Wait.forListeningPorts(SMTP_PORT, API_PORT));
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    static {
        MAILPIT.start();
    }

    @DynamicPropertySource
    static void mailServer(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", MAILPIT::getHost);
        registry.add("spring.mail.port", () -> MAILPIT.getMappedPort(SMTP_PORT));
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    protected record Mail(String subject, String text) {
    }

    protected String asUser() {
        return TestTokens.bearer(UUID.randomUUID(), "USER");
    }

    protected String asAdmin() {
        return TestTokens.bearer(UUID.randomUUID(), "ADMIN");
    }

    protected void publish(String routingKey, Object fact) {
        rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE, routingKey, fact);
    }

    protected List<Mail> mailsTo(String address) {
        String query = URLEncoder.encode("to:" + address, StandardCharsets.UTF_8);
        List<String> ids = JsonPath.read(mailpit("GET", "/api/v1/search?query=" + query), "$.messages[*].ID");

        return ids.stream()
                .map(id -> mailpit("GET", "/api/v1/message/" + id))
                .map(message -> new Mail(JsonPath.read(message, "$.Subject"), JsonPath.read(message, "$.Text")))
                .toList();
    }

    private static String mailpit(String method, String path) {
        URI uri = URI.create("http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(API_PORT) + path);

        try {
            return HTTP.send(HttpRequest.newBuilder(uri).method(method, HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.ofString()).body();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    @BeforeEach
    void cleanState() {
        jdbcTemplate.execute("DELETE FROM notifications");
        jdbcTemplate.execute("DELETE FROM contacts");
        mailpit("DELETE", "/api/v1/messages");
    }
}
