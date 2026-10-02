package com.matheus.orderFlow.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Extends AbstractIntegrationTest to inherit the test configuration and setup
class ProductIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldPersistProductWithGeneratedIdAndAuditFields() throws Exception {
        String json = """
                {
                    "name": "Notebook",
                    "description": "Notebook para trabalho",
                    "price": 3500.00
                }
                """;

        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Notebook"))
                .andExpect(jsonPath("$.price").value(3500.00))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    void shouldCreateAndGetProduct() throws Exception {
        String json = """
                {
                    "name": "Smartphone",
                    "description": "Smartphone de última geração",
                    "price": 2500.00
                }
                """;

        String response = mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String id = JsonPath.read(response, "$.id");

        mockMvc.perform(get("/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Smartphone"))
                .andExpect(jsonPath("$.price").value(2500.00));
    }

    @Test
    void shouldDeleteProductAndReturnNotFoundAfterwards() throws Exception {
        String json = """
                {
                    "name": "Mouse",
                    "description": "Mouse sem fio",
                    "price": 150.00
                }
                """;

        String response = mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String id = JsonPath.read(response, "$.id");

        mockMvc.perform(delete("/products/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/products/{id}", id))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void shouldReturnNotFoundForUnknownId() throws Exception {
        mockMvc.perform(get("/products/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    private void createProducts(int quantity) throws Exception {
        for (int index = 0; index < quantity; index++) {
            mockMvc.perform(post("/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "name": "Produto %02d",
                                        "description": "Descricao",
                                        "price": 10.00
                                    }
                                    """.formatted(index)))
                    .andExpect(status().isCreated());
        }
    }

    @Test
    void shouldSplitProductsAcrossPages() throws Exception {
        createProducts(25);

        mockMvc.perform(get("/products").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(10))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(25))
                .andExpect(jsonPath("$.totalPages").value(3));

        mockMvc.perform(get("/products").param("size", "10").param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(5))
                .andExpect(jsonPath("$.page").value(2));
    }

    @Test
    void shouldApplyTheDefaultPageSize() throws Exception {
        createProducts(1);

        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void shouldReturnTheNewestProductsFirstByDefault() throws Exception {
        createProducts(3);

        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Produto 02"))
                .andExpect(jsonPath("$.content[2].name").value("Produto 00"));
    }

    @Test
    void shouldReturnAnEmptyPageBeyondTheLastOne() throws Exception {
        createProducts(3);

        mockMvc.perform(get("/products").param("page", "99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void shouldCapThePageSizeAtTheConfiguredMaximum() throws Exception {
        createProducts(3);

        mockMvc.perform(get("/products").param("size", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void shouldSortByTheRequestedField() throws Exception {
        createProducts(3);

        mockMvc.perform(get("/products").param("sort", "name,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Produto 00"))
                .andExpect(jsonPath("$.content[2].name").value("Produto 02"));

        mockMvc.perform(get("/products").param("sort", "name,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Produto 02"));
    }

    @Test
    void shouldRejectSortingByAnUnknownField() throws Exception {
        createProducts(1);

        mockMvc.perform(get("/products").param("sort", "doesNotExist,asc"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void shouldRejectDescriptionLongerThanColumnLimit() throws Exception {
        String json = """
                {
                    "name": "Monitor",
                    "description": "%s",
                    "price": 1200.00
                }
                """.formatted("a".repeat(1001));

        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }
}
