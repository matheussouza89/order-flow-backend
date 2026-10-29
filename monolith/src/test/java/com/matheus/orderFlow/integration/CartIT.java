package com.matheus.orderFlow.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CartIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private String createProduct(String name, String price) throws Exception {
        String response = mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "%s",
                                    "description": "Descricao",
                                    "price": %s
                                }
                                """.formatted(name, price)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return JsonPath.read(response, "$.id");
    }

    private void addItem(String productId, int quantity) throws Exception {
        mockMvc.perform(post("/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "productId": "%s", "quantity": %d }
                                """.formatted(productId, quantity)))
                .andExpect(status().isOk());
    }

    @Test
    void shouldCreateCartOnFirstItem() throws Exception {
        String productId = createProduct("Teclado", "100.00");

        mockMvc.perform(get("/cart"))
                .andExpect(status().isNotFound());

        addItem(productId, 2);

        mockMvc.perform(get("/cart"))
                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productName").value("Teclado"))
                .andExpect(jsonPath("$.items[0].price").value(100.00))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.total").value(200.00));
    }

    @Test
    void shouldReflectTheCurrentCatalogPrice() throws Exception {
        String productId = createProduct("Monitor", "800.00");
        addItem(productId, 1);

        mockMvc.perform(get("/cart"))
                .andExpect(jsonPath("$.total").value(800.00));

        mockMvc.perform(put("/products/{id}", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Monitor",
                                    "description": "Descricao",
                                    "price": 1200.00
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/cart"))
                .andExpect(jsonPath("$.items[0].price").value(1200.00))
                .andExpect(jsonPath("$.total").value(1200.00));
    }

    @Test
    void shouldSumQuantityWhenAddingTheSameProductTwice() throws Exception {
        String productId = createProduct("Mouse", "50.00");

        addItem(productId, 2);
        addItem(productId, 3);

        mockMvc.perform(get("/cart"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(5))
                .andExpect(jsonPath("$.total").value(250.00));
    }

    @Test
    void shouldReplaceQuantityAndRemoveWhenSetToZero() throws Exception {
        String productId = createProduct("Headset", "300.00");
        addItem(productId, 4);

        mockMvc.perform(put("/cart/items/{productId}", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"quantity\": 2 }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.total").value(600.00));

        mockMvc.perform(put("/cart/items/{productId}", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"quantity\": 0 }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void shouldRemoveItem() throws Exception {
        String productId = createProduct("Webcam", "250.00");
        addItem(productId, 1);

        mockMvc.perform(delete("/cart/items/{productId}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void shouldTurnCartIntoOrderAndDiscardIt() throws Exception {
        String keyboardId = createProduct("Teclado", "100.00");
        String mouseId = createProduct("Mouse", "50.00");

        addItem(keyboardId, 2);
        addItem(mouseId, 3);

        String response = mockMvc.perform(post("/cart/checkout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.total").value(350.00))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String orderId = JsonPath.read(response, "$.id");

        mockMvc.perform(get("/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(350.00));

        mockMvc.perform(get("/cart"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldFreezePriceAtCheckout() throws Exception {
        String productId = createProduct("Notebook", "3000.00");
        addItem(productId, 1);

        String response = mockMvc.perform(post("/cart/checkout"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String orderId = JsonPath.read(response, "$.id");

        mockMvc.perform(put("/products/{id}", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Notebook",
                                    "description": "Descricao",
                                    "price": 4500.00
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/orders/{id}", orderId))
                .andExpect(jsonPath("$.items[0].unitPrice").value(3000.00))
                .andExpect(jsonPath("$.total").value(3000.00));
    }

    @Test
    void shouldRejectCheckoutOfAnEmptyCart() throws Exception {
        String productId = createProduct("Cabo", "20.00");
        addItem(productId, 1);
        mockMvc.perform(delete("/cart/items/{productId}", productId))
                .andExpect(status().isOk());

        mockMvc.perform(post("/cart/checkout"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.items").isNotEmpty());
    }

    @Test
    void shouldIgnoreItemsWhoseProductWasRemovedFromTheCatalog() throws Exception {
        String keptId = createProduct("Teclado", "100.00");
        String removedId = createProduct("Descontinuado", "70.00");

        addItem(keptId, 1);
        addItem(removedId, 2);

        mockMvc.perform(get("/cart"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.total").value(240.00));

        mockMvc.perform(delete("/products/{id}", removedId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productName").value("Teclado"))
                .andExpect(jsonPath("$.total").value(100.00));
    }

    @Test
    void shouldRejectCheckoutWhenAProductIsNoLongerAvailable() throws Exception {
        String keptId = createProduct("Teclado", "100.00");
        String removedId = createProduct("Descontinuado", "70.00");

        addItem(keptId, 1);
        addItem(removedId, 2);

        mockMvc.perform(delete("/products/{id}", removedId))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/cart/checkout"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString(removedId)))
                .andExpect(jsonPath("$.errors").isEmpty());

        mockMvc.perform(delete("/cart/items/{productId}", removedId))
                .andExpect(status().isOk());

        mockMvc.perform(post("/cart/checkout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.total").value(100.00));
    }

    @Test
    void shouldListEveryUnavailableProductAtOnce() throws Exception {
        String keptId = createProduct("Teclado", "100.00");
        String firstRemovedId = createProduct("Descontinuado", "70.00");
        String secondRemovedId = createProduct("Esgotado", "30.00");

        addItem(keptId, 1);
        addItem(firstRemovedId, 1);
        addItem(secondRemovedId, 1);

        mockMvc.perform(delete("/products/{id}", firstRemovedId))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/products/{id}", secondRemovedId))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/cart/checkout"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString(firstRemovedId)))
                .andExpect(jsonPath("$.message").value(containsString(secondRemovedId)));
    }

    @Test
    void shouldKeepTheCartWhenCheckoutIsRejected() throws Exception {
        String productId = createProduct("Descontinuado", "70.00");
        addItem(productId, 2);

        mockMvc.perform(delete("/products/{id}", productId))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/cart/checkout"))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/cart"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReturnNotFoundWhenTheCallerHasNoCart() throws Exception {
        mockMvc.perform(get("/cart"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldKeepEachUserCartSeparate() throws Exception {
        String productId = createProduct("Teclado", "100.00");

        mockMvc.perform(post("/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "productId": "%s", "quantity": 2 }
                                """.formatted(productId)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/cart").header(HttpHeaders.AUTHORIZATION, asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(get("/cart").header(HttpHeaders.AUTHORIZATION, asAnotherUser()))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnNotFoundWhenAddingUnknownProduct() throws Exception {
        mockMvc.perform(post("/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "productId": "%s", "quantity": 1 }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }
}
