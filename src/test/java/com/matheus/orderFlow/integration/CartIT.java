package com.matheus.orderFlow.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CartIT extends AbstractIntegrationTest {

    private static final String CART_ID = "user-1";

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
        mockMvc.perform(post("/carts/{cartId}/items", CART_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "productId": "%s", "quantity": %d }
                                """.formatted(productId, quantity)))
                .andExpect(status().isOk());
    }

    @Test
    void shouldCreateCartOnFirstItem() throws Exception {
        String productId = createProduct("Teclado", "100.00");

        mockMvc.perform(get("/carts/{cartId}", CART_ID))
                .andExpect(status().isNotFound());

        addItem(productId, 2);

        mockMvc.perform(get("/carts/{cartId}", CART_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CART_ID))
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

        mockMvc.perform(get("/carts/{cartId}", CART_ID))
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

        mockMvc.perform(get("/carts/{cartId}", CART_ID))
                .andExpect(jsonPath("$.items[0].price").value(1200.00))
                .andExpect(jsonPath("$.total").value(1200.00));
    }

    @Test
    void shouldSumQuantityWhenAddingTheSameProductTwice() throws Exception {
        String productId = createProduct("Mouse", "50.00");

        addItem(productId, 2);
        addItem(productId, 3);

        mockMvc.perform(get("/carts/{cartId}", CART_ID))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(5))
                .andExpect(jsonPath("$.total").value(250.00));
    }

    @Test
    void shouldReplaceQuantityAndRemoveWhenSetToZero() throws Exception {
        String productId = createProduct("Headset", "300.00");
        addItem(productId, 4);

        mockMvc.perform(put("/carts/{cartId}/items/{productId}", CART_ID, productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"quantity\": 2 }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.total").value(600.00));

        mockMvc.perform(put("/carts/{cartId}/items/{productId}", CART_ID, productId)
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

        mockMvc.perform(delete("/carts/{cartId}/items/{productId}", CART_ID, productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void shouldTurnCartIntoOrderAndDiscardIt() throws Exception {
        String keyboardId = createProduct("Teclado", "100.00");
        String mouseId = createProduct("Mouse", "50.00");

        addItem(keyboardId, 2);
        addItem(mouseId, 3);

        String response = mockMvc.perform(post("/carts/{cartId}/checkout", CART_ID))
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

        mockMvc.perform(get("/carts/{cartId}", CART_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldFreezePriceAtCheckout() throws Exception {
        String productId = createProduct("Notebook", "3000.00");
        addItem(productId, 1);

        String response = mockMvc.perform(post("/carts/{cartId}/checkout", CART_ID))
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
        mockMvc.perform(delete("/carts/{cartId}/items/{productId}", CART_ID, productId))
                .andExpect(status().isOk());

        mockMvc.perform(post("/carts/{cartId}/checkout", CART_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.items").isNotEmpty());
    }

    @Test
    void shouldIgnoreItemsWhoseProductWasRemovedFromTheCatalog() throws Exception {
        String keptId = createProduct("Teclado", "100.00");
        String removedId = createProduct("Descontinuado", "70.00");

        addItem(keptId, 1);
        addItem(removedId, 2);

        mockMvc.perform(get("/carts/{cartId}", CART_ID))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.total").value(240.00));

        mockMvc.perform(delete("/products/{id}", removedId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/carts/{cartId}", CART_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productName").value("Teclado"))
                .andExpect(jsonPath("$.total").value(100.00));
    }

    @Test
    void shouldReturnNotFoundForUnknownCart() throws Exception {
        mockMvc.perform(get("/carts/{cartId}", "does-not-exist"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnNotFoundWhenAddingUnknownProduct() throws Exception {
        mockMvc.perform(post("/carts/{cartId}/items", CART_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "productId": "%s", "quantity": 1 }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }
}
