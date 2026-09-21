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

        mockMvc.perform(post("/product")
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

        String response = mockMvc.perform(post("/product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String id = JsonPath.read(response, "$.id");

        mockMvc.perform(get("/product/{id}", id))
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

        String response = mockMvc.perform(post("/product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String id = JsonPath.read(response, "$.id");

        mockMvc.perform(delete("/product/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/product/{id}", id))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/product"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void shouldReturnNotFoundForUnknownId() throws Exception {
        mockMvc.perform(get("/product/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
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

        mockMvc.perform(post("/product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }
}
