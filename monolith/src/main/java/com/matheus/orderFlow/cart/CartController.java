package com.matheus.orderFlow.cart;

import com.matheus.orderFlow.order.OrderResponse;
import com.matheus.orderFlow.shared.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(
        name = "Carts",
        description = "Shopping cart endpoints. The cart belongs to the authenticated caller."
)
@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
class CartController {
    private final CartService cartService;

    @Operation(summary = "Get cart",
            description = "Returns the caller's cart, with the current catalog price of each item.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cart found"),
            @ApiResponse(responseCode = "404", description = "The caller has no cart",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public CartResponse getCart() {
        return cartService.getCart();
    }

    @Operation(summary = "Add item to cart",
            description = "Adds to the current quantity. The cart is created on the first item.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Item added"),
            @ApiResponse(responseCode = "400", description = "Invalid quantity",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Product not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/items")
    public CartResponse addItem(@RequestBody CartItemDto item) {
        return cartService.addItem(item);
    }

    @Operation(summary = "Set item quantity",
            description = "Replaces the quantity of an item. Zero removes it from the cart.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Quantity updated"),
            @ApiResponse(responseCode = "400", description = "Negative quantity",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No cart, or product not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/items/{productId}")
    public CartResponse setItemQuantity(@PathVariable UUID productId,
                                        @RequestBody QuantityDto quantity) {
        return cartService.setItemQuantity(productId, quantity.quantity());
    }

    @Operation(summary = "Remove item from cart")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Item removed"),
            @ApiResponse(responseCode = "404", description = "The caller has no cart",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/items/{productId}")
    public CartResponse removeItem(@PathVariable UUID productId) {
        return cartService.removeItem(productId);
    }

    @Operation(summary = "Checkout",
            description = "Creates an order from the cart, freezing the current prices, and discards the cart.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order created"),
            @ApiResponse(responseCode = "400", description = "Cart is empty",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "The caller has no cart",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409",
                    description = "Cart contains a product that is no longer available",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/checkout")
    public OrderResponse checkout() {
        return cartService.checkout();
    }
}
