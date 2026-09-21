package com.matheus.orderFlow.product;

import com.matheus.orderFlow.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductRepository productRepository;

    public ProductResponse createProduct(ProductDto dto) {
        Product newProduct = new Product(dto.name(), dto.description(), dto.price());
        return ProductResponse.from(productRepository.save(newProduct));
    }

    public ProductResponse getProduct(UUID id) {
        return ProductResponse.from(findOrThrow(id));
    }

    public List<ProductResponse> getAllProducts() {
        return productRepository.findAll()
                .stream()
                .map(ProductResponse::from)
                .toList();
    }

    public ProductResponse updateProduct(UUID id, ProductDto dto) {
        Product existingProduct = findOrThrow(id);
        existingProduct.update(dto.name(), dto.description(), dto.price());
        return ProductResponse.from(productRepository.save(existingProduct));
    }

    public void deleteProduct(UUID id) {
        if (!productRepository.existsById(id)) {
            throw new NotFoundException(id);
        }

        productRepository.deleteById(id);
    }

    private Product findOrThrow(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(id));
    }
}
