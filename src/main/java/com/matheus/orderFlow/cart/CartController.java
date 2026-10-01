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
        description = "Shopping cart endpoints"
)
@RestController
@RequestMapping("/carts")
@RequiredArgsConstructor
class CartController {
    private final CartService cartService;

    @Operation(summary = "Get cart",
            description = "Returns the cart with the current catalog price of each item.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cart found"),
            @ApiResponse(responseCode = "404", description = "Cart not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{cartId}")
    public CartResponse getCart(@PathVariable String cartId) {
        return cartService.getCart(cartId);
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
    @PostMapping("/{cartId}/items")
    public CartResponse addItem(@PathVariable String cartId, @RequestBody CartItemDto item) {
        return cartService.addItem(cartId, item);
    }

    @Operation(summary = "Set item quantity",
            description = "Replaces the quantity of an item. Zero removes it from the cart.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Quantity updated"),
            @ApiResponse(responseCode = "400", description = "Negative quantity",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Cart or product not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/{cartId}/items/{productId}")
    public CartResponse setItemQuantity(@PathVariable String cartId,
                                        @PathVariable UUID productId,
                                        @RequestBody QuantityDto quantity) {
        return cartService.setItemQuantity(cartId, productId, quantity.quantity());
    }

    @Operation(summary = "Remove item from cart")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Item removed"),
            @ApiResponse(responseCode = "404", description = "Cart not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{cartId}/items/{productId}")
    public CartResponse removeItem(@PathVariable String cartId, @PathVariable UUID productId) {
        return cartService.removeItem(cartId, productId);
    }

    @Operation(summary = "Checkout",
            description = "Creates an order from the cart, freezing the current prices, and discards the cart.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order created"),
            @ApiResponse(responseCode = "400", description = "Cart is empty",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Cart not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409",
                    description = "Cart contains a product that is no longer available",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{cartId}/checkout")
    public OrderResponse checkout(@PathVariable String cartId) {
        return cartService.checkout(cartId);
    }
}
