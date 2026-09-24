package com.matheus.orderFlow.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OrderIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private String createProduct(String name, String price) throws Exception {
        String json = """
                {
                    "name": "%s",
                    "description": "Descricao",
                    "price": %s
                }
                """.formatted(name, price);

        String response = mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return JsonPath.read(response, "$.id");
    }

    private String orderJson(String productId, int quantity) {
        return """
                {
                    "items": [
                        { "productId": "%s", "quantity": %d }
                    ]
                }
                """.formatted(productId, quantity);
    }

    @Test
    void shouldCreateOrderTakingNameAndPriceFromCatalog() throws Exception {
        String productId = createProduct("Teclado", "100.00");

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson(productId, 2)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.total").value(200.00))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productId").value(productId))
                .andExpect(jsonPath("$.items[0].productName").value("Teclado"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(100.00))
                .andExpect(jsonPath("$.items[0].subtotal").value(200.00));
    }

    @Test
    void shouldPersistItemsLinkedToTheOrder() throws Exception {
        String productId = createProduct("Mouse", "50.00");

        String response = mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson(productId, 3)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String orderId = JsonPath.read(response, "$.id");

        mockMvc.perform(get("/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productName").value("Mouse"))
                .andExpect(jsonPath("$.items[0].quantity").value(3))
                .andExpect(jsonPath("$.total").value(150.00));
    }

    @Test
    void shouldKeepOriginalPriceAfterProductIsUpdated() throws Exception {
        String productId = createProduct("Monitor", "800.00");

        String response = mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson(productId, 1)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String orderId = JsonPath.read(response, "$.id");

        String updatedProduct = """
                {
                    "name": "Monitor 4K",
                    "description": "Descricao",
                    "price": 1200.00
                }
                """;

        mockMvc.perform(put("/products/{id}", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatedProduct))
                .andExpect(status().isOk());

        mockMvc.perform(get("/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].productName").value("Monitor"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(800.00))
                .andExpect(jsonPath("$.total").value(800.00));
    }

    @Test
    void shouldSumTotalAcrossSeveralItems() throws Exception {
        String keyboardId = createProduct("Teclado", "100.00");
        String mouseId = createProduct("Mouse", "50.00");

        String json = """
                {
                    "items": [
                        { "productId": "%s", "quantity": 2 },
                        { "productId": "%s", "quantity": 3 }
                    ]
                }
                """.formatted(keyboardId, mouseId);

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.total").value(350.00));
    }

    @Test
    void shouldReturnNotFoundWhenProductDoesNotExist() throws Exception {
        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson(UUID.randomUUID().toString(), 1)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void shouldRejectOrderWithoutItems() throws Exception {
        String json = """
                {
                    "items": []
                }
                """;

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.items").isNotEmpty());
    }

    @Test
    void shouldReturnNotFoundForUnknownOrder() throws Exception {
        mockMvc.perform(get("/orders/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    private String createOrder(String productId) throws Exception {
        String response = mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson(productId, 1)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return JsonPath.read(response, "$.id");
    }

    private void transition(String orderId, String action, String expectedStatus) throws Exception {
        mockMvc.perform(post("/orders/{id}/{action}", orderId, action))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(expectedStatus));
    }

    @Test
    void shouldWalkThroughTheWholeDeliveryFlow() throws Exception {
        String orderId = createOrder(createProduct("Teclado", "100.00"));

        mockMvc.perform(get("/orders/{id}", orderId))
                .andExpect(jsonPath("$.status").value("PENDING"));

        transition(orderId, "confirm", "CONFIRMED");
        transition(orderId, "ship", "SHIPPED");
        transition(orderId, "deliver", "DELIVERED");

        mockMvc.perform(get("/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));
    }

    @Test
    void shouldPersistStatusBetweenRequests() throws Exception {
        String orderId = createOrder(createProduct("Mouse", "50.00"));

        transition(orderId, "confirm", "CONFIRMED");

        mockMvc.perform(get("/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("CONFIRMED"));
    }

    @Test
    void shouldCancelOrderBeforeShipping() throws Exception {
        String orderId = createOrder(createProduct("Monitor", "800.00"));

        transition(orderId, "confirm", "CONFIRMED");
        transition(orderId, "cancel", "CANCELLED");

        mockMvc.perform(get("/orders/{id}", orderId))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void shouldRejectCancellingAShippedOrder() throws Exception {
        String orderId = createOrder(createProduct("Headset", "300.00"));

        transition(orderId, "confirm", "CONFIRMED");
        transition(orderId, "ship", "SHIPPED");

        mockMvc.perform(post("/orders/{id}/cancel", orderId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").isNotEmpty());

        mockMvc.perform(get("/orders/{id}", orderId))
                .andExpect(jsonPath("$.status").value("SHIPPED"));
    }

    @Test
    void shouldRejectSkippingAStepInTheFlow() throws Exception {
        String orderId = createOrder(createProduct("Webcam", "250.00"));

        mockMvc.perform(post("/orders/{id}/ship", orderId))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/orders/{id}", orderId))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void shouldReturnNotFoundWhenConfirmingUnknownOrder() throws Exception {
        mockMvc.perform(post("/orders/{id}/confirm", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
