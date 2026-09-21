package com.matheus.orderFlow.product;

import com.matheus.orderFlow.shared.exception.DomainValidationException;
import com.matheus.orderFlow.shared.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
public class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    @Test
    void shouldGetProductById() {
        UUID id = UUID.randomUUID();

        Product product = new Product(
                "Notebook",
                "Notebook para trabalho",
                new BigDecimal("3500.00")
        );

        when(productRepository.findById(id))
                .thenReturn(Optional.of(product));

        ProductResponse result = productService.getProduct(id);

        assertNotNull(result);
        assertEquals("Notebook", result.name());

        verify(productRepository).findById(id);
    }

    @Test
    void shouldGetAllProducts() {
        Product product = new Product(
                "Notebook",
                "Notebook para trabalho",
                new BigDecimal("3500.00")
        );

        when(productRepository.findAll()).thenReturn(List.of(product));

        List<ProductResponse> result = productService.getAllProducts();
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Notebook", result.get(0).name());
        assertEquals("Notebook para trabalho", result.get(0).description());
        assertEquals(
                new BigDecimal("3500.00"),
                result.get(0).price()
        );
        verify(productRepository).findAll();
    }

    @Test
    void shouldReturnEmptyListWhenThereAreNoProducts() {
        when(productRepository.findAll()).thenReturn(List.of());

        List<ProductResponse> result = productService.getAllProducts();

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(productRepository).findAll();
    }

    @Test
    void shouldCreateProduct() {
        ProductDto dto = new ProductDto(
                "Notebook",
                "Notebook para trabalho",
                new BigDecimal("3500.00")
        );

        Product savedProduct = new Product(
                dto.name(),
                dto.description(),
                dto.price()
        );

        when(productRepository.save(any(Product.class)))
                .thenReturn(savedProduct);

        ProductResponse result = productService.createProduct(dto);

        assertNotNull(result);
        assertEquals("Notebook", result.name());

        verify(productRepository)
                .save(any(Product.class));

    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   "})
    void shouldRejectInvalidProductNameWhenCreating(String name) {
        ProductDto dto = new ProductDto(
                name,
                "Description",
                new BigDecimal("100.00")
        );

        assertThrows(
                DomainValidationException.class,
                () -> productService.createProduct(dto)
        );

        verify(productRepository, never()).save(any(Product.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "-100.00"})
    void shouldRejectInvalidProductPriceWhenCreating(String price) {
        ProductDto dto = new ProductDto(
                "Notebook",
                "Description",
                new BigDecimal(price)
        );

        assertThrows(
                DomainValidationException.class,
                () -> productService.createProduct(dto)
        );

        verify(productRepository, never()).save(any(Product.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   "})
    void shouldRejectInvalidProductNameWhenUpdating(String name) {

        UUID id = UUID.randomUUID();

        Product existingProduct = new Product(
                "Notebook",
                "Notebook para trabalho",
                new BigDecimal("3500.00")
        );

        ProductDto dto = new ProductDto(
                name,
                "Description",
                new BigDecimal("100.00")
        );

        when(productRepository.findById(id))
                .thenReturn(Optional.of(existingProduct));

        assertThrows(
                DomainValidationException.class,
                () -> productService.updateProduct(id, dto)
        );

        verify(productRepository).findById(id);
        verify(productRepository, never()).save(any(Product.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "-100.00"})
    void shouldRejectInvalidProductPriceWhenUpdating(String price) {

        UUID id = UUID.randomUUID();

        Product existingProduct = new Product(
                "Notebook",
                "Notebook para trabalho",
                new BigDecimal("3500.00")
        );

        ProductDto dto = new ProductDto(
                "Notebook Atualizado",
                "Description",
                new BigDecimal(price)
        );

        when(productRepository.findById(id))
                .thenReturn(Optional.of(existingProduct));

        assertThrows(
                DomainValidationException.class,
                () -> productService.updateProduct(id, dto)
        );

        verify(productRepository).findById(id);
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void shouldThrowExceptionWhenProductNotFound() {

        UUID id = UUID.randomUUID();

        when(productRepository.findById(id))
                .thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> productService.getProduct(id)
        );

        verify(productRepository).findById(id);
    }

    @Test
    void shouldUpdateProduct() {
        UUID id = UUID.randomUUID();

        Product existingProduct = new Product(
                "Notebook",
                "Notebook para trabalho",
                new BigDecimal("3500.00")
        );

        ProductDto dto = new ProductDto(
                "Notebook Atualizado",
                "Notebook Atualizado para trabalho",
                new BigDecimal("4500.00")
        );

        when(productRepository.findById(id))
                .thenReturn(Optional.of(existingProduct));

        when(productRepository.save(existingProduct))
                .thenReturn(existingProduct);

        ProductResponse result = productService.updateProduct(id, dto);

        assertEquals("Notebook Atualizado", result.name());
        assertEquals("Notebook Atualizado para trabalho", result.description());
        assertEquals(
                new BigDecimal("4500.00"),
                result.price()
        );

        verify(productRepository).save(existingProduct);
        verify(productRepository).findById(id);
    }

    @Test
    void shouldThrowExceptionWhenUpdateProductNotFound() {
        UUID id = UUID.randomUUID();

        ProductDto dto = new ProductDto(
                "Notebook",
                "Notebook atualizado",
                new BigDecimal("4500.00")
        );

        when(productRepository.findById(id))
                .thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> productService.updateProduct(id, dto)
        );

        verify(productRepository).findById(id);
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void shouldDeleteProduct() {
        UUID id = UUID.randomUUID();

        when(productRepository.existsById(id)).thenReturn(true);

        productService.deleteProduct(id);

        verify(productRepository).existsById(id);
        verify(productRepository).deleteById(id);
    }

    @Test
    void shouldThrowExceptionWhenDeletingNonExistingProduct() {
        UUID id = UUID.randomUUID();
        when(productRepository.existsById(id)).thenReturn(false);

        assertThrows(
                NotFoundException.class,
                () -> productService.deleteProduct(id)
        );

        verify(productRepository).existsById(id);
        verify(productRepository, never()).deleteById(any(UUID.class));
    }
}
