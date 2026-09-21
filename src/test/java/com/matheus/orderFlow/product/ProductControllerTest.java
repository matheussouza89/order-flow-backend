package com.matheus.orderFlow.product;

import com.matheus.orderFlow.shared.exception.DomainValidationException;
import com.matheus.orderFlow.shared.exception.NotFoundException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
public class ProductControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;
    @Autowired
    private ObjectMapper objectMapper;

    private ProductResponse response(String name, String description, BigDecimal price) {
        return new ProductResponse(
                UUID.randomUUID(), name, description, price,
                Instant.now(), Instant.now()
        );
    }

    @Test
    void shouldGetProductById() throws Exception {
        UUID id = UUID.randomUUID();

        ProductResponse product = response(
                "Notebook",
                "Notebook para trabalho",
                new BigDecimal("3500.00")
        );

        when(productService.getProduct(id))
                .thenReturn(product);

        mockMvc.perform(get("/product/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Notebook"))
                .andExpect(jsonPath("$.description")
                        .value("Notebook para trabalho"))
                .andExpect(jsonPath("$.price").value(3500.00));

        verify(productService).getProduct(id);
    }

    @Test
    void shouldThrowNotFoundExceptionWhenProductNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(productService.getProduct(id)).thenThrow(NotFoundException.class);
        mockMvc.perform(get("/product/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void shouldGetAllProducts() throws Exception {
        ProductResponse product = response(
                "Notebook",
                "Notebook para trabalho",
                new BigDecimal("3500.00")
        );

        when(productService.getAllProducts())
                .thenReturn(List.of(product));

        mockMvc.perform(get("/product"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Notebook"))
                .andExpect(jsonPath("$[0].description")
                        .value("Notebook para trabalho"))
                .andExpect(jsonPath("$[0].price").value(3500.00));

        verify(productService).getAllProducts();
    }

    @Test
    void shouldReturnEmptyListWhenThereAreNoProducts() throws Exception {
        when(productService.getAllProducts()).thenReturn(List.of());

        mockMvc.perform(get("/product"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));

        verify(productService).getAllProducts();
    }

    @Test
    void shouldUpdateProduct() throws Exception {
        UUID id = UUID.randomUUID();

        ProductDto dto = new ProductDto(
                "Notebook Atualizado",
                "Notebook Atualizado para trabalho",
                new BigDecimal("4500.00")
        );

        ProductResponse updatedProduct = response(
                dto.name(),
                dto.description(),
                dto.price()
        );

        when(productService.updateProduct(id, dto))
                .thenReturn(updatedProduct);

        mockMvc.perform(
                        put("/product/{id}", id)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(dto))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name")
                        .value("Notebook Atualizado"))
                .andExpect(jsonPath("$.description")
                        .value("Notebook Atualizado para trabalho"))
                .andExpect(jsonPath("$.price")
                        .value(4500.00));

        verify(productService).updateProduct(id, dto);
    }

    @Test
    void shouldThrowNotFoundExceptionWhenUpdateProductNotFound() throws Exception {
        UUID id = UUID.randomUUID();

        ProductDto dto = new ProductDto(
                "Notebook Atualizado",
                "Notebook Atualizado para trabalho",
                new BigDecimal("4500.00")
        );

        when(productService.updateProduct(id, dto)).thenThrow(NotFoundException.class);
        mockMvc.perform(put("/product/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "-100.00"})
    void shouldReturnBadRequestWhenUpdatePriceIsInvalid(String price)
            throws Exception {

        UUID id = UUID.randomUUID();

        String invalidJson = """
            {
                "name": "Notebook",
                "description": "Notebook para trabalho",
                "price": %s
            }
            """.formatted(price);

        when(productService.updateProduct(any(UUID.class), any(ProductDto.class)))
                .thenThrow(new DomainValidationException(
                        "price", "Product price cannot be negative"));

        mockMvc.perform(put("/product/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.price")
                        .value("Product price cannot be negative"));

        verify(productService)
                .updateProduct(any(UUID.class), any(ProductDto.class));
    }

    @Test
    void shouldCreateProduct() throws Exception {
        ProductDto dto = new ProductDto(
                "Notebook",
                "Notebook para trabalho",
                new BigDecimal("3500.00")
        );

        ProductResponse createdProduct = response(
                dto.name(),
                dto.description(),
                dto.price()
        );

        when(productService.createProduct(any(ProductDto.class))).thenReturn(createdProduct);

        mockMvc.perform(post("/product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.name").value("Notebook"))
                .andExpect(jsonPath("$.description").value("Notebook para trabalho"))
                .andExpect(jsonPath("$.price").value(3500.00));

        verify(productService).createProduct(any(ProductDto.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "-100.00"})
    void shouldReturnBadRequestWhenCreatePriceIsInvalid(String price)
            throws Exception {

        String invalidJson = """
                {
                    "name": "Notebook",
                    "description": "Notebook para trabalho",
                    "price": %s
                }
                """.formatted(price);

        when(productService.createProduct(any(ProductDto.class)))
                .thenThrow(new DomainValidationException(
                        "price", "Product price cannot be negative"));

        mockMvc.perform(post("/product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.price")
                        .value("Product price cannot be negative"));

        verify(productService).createProduct(any(ProductDto.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   "})
    void shouldReturnBadRequestWhenCreateNameIsInvalid(String name)
            throws Exception {

        String invalidJson = """
                {
                    "name": "%s",
                    "description": "Notebook para trabalho",
                    "price": 3500.00
                }
                """.formatted(name);

        when(productService.createProduct(any(ProductDto.class)))
                .thenThrow(new DomainValidationException(
                        "name", "Product name cannot be empty"));

        mockMvc.perform(post("/product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name")
                        .value("Product name cannot be empty"));

        verify(productService).createProduct(any(ProductDto.class));
    }

    @Test
    void shouldReturnBadRequestWhenJsonIsInvalid() throws Exception {
        String invalidJson = """
                {
                    "name": "Notebook",
                    "price":
                """;

        mockMvc.perform(post("/product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());

        verify(productService, never()).createProduct(any());
    }

    @Test
    void shouldDeleteProduct() throws Exception {
        UUID id = UUID.randomUUID();

        doNothing().when(productService).deleteProduct(id);

        mockMvc.perform(delete("/product/{id}", id))
                .andExpect(status().isNoContent());

        verify(productService).deleteProduct(id);
    }

    @Test
    void shouldThrowNotFoundExceptionWhenDeleteProductNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(NotFoundException.class).when(productService).deleteProduct(id);

        mockMvc.perform(delete("/product/{id}", id)).andExpect(status().isNotFound());
    }
}
