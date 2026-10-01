package com.matheus.orderFlow.cart;

import com.matheus.orderFlow.shared.exception.DomainValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CartTest {

    private final UUID productId = UUID.randomUUID();

    @Test
    void shouldStartEmpty() {
        Cart cart = new Cart("user-1");

        assertTrue(cart.isEmpty());
        assertEquals("user-1", cart.getId());
    }

    @Test
    void shouldAddItem() {
        Cart cart = new Cart("user-1");

        cart.addItem(productId, 2);

        assertEquals(2, cart.getItems().get(productId));
        assertFalse(cart.isEmpty());
    }

    @Test
    void shouldSumQuantityWhenAddingTheSameProductTwice() {
        Cart cart = new Cart("user-1");

        cart.addItem(productId, 2);
        cart.addItem(productId, 3);

        assertEquals(5, cart.getItems().get(productId));
        assertEquals(1, cart.getItems().size());
    }

    @Test
    void shouldReplaceQuantityWhenSettingIt() {
        Cart cart = new Cart("user-1");
        cart.addItem(productId, 5);

        cart.setItemQuantity(productId, 2);

        assertEquals(2, cart.getItems().get(productId));
    }

    @Test
    void shouldRemoveItemWhenQuantityIsSetToZero() {
        Cart cart = new Cart("user-1");
        cart.addItem(productId, 5);

        cart.setItemQuantity(productId, 0);

        assertTrue(cart.isEmpty());
    }

    @Test
    void shouldRemoveItem() {
        Cart cart = new Cart("user-1");
        cart.addItem(productId, 5);

        cart.removeItem(productId);

        assertTrue(cart.isEmpty());
    }

    @Test
    void shouldKeepOtherItemsWhenRemovingOne() {
        Cart cart = new Cart("user-1");
        UUID otherProductId = UUID.randomUUID();

        cart.addItem(productId, 1);
        cart.addItem(otherProductId, 4);

        cart.removeItem(productId);

        assertEquals(1, cart.getItems().size());
        assertEquals(4, cart.getItems().get(otherProductId));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -10})
    void shouldRejectAddingNonPositiveQuantity(int quantity) {
        Cart cart = new Cart("user-1");

        assertThrows(DomainValidationException.class, () -> cart.addItem(productId, quantity));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, -10})
    void shouldRejectNegativeQuantityWhenSettingIt(int quantity) {
        Cart cart = new Cart("user-1");

        assertThrows(DomainValidationException.class,
                () -> cart.setItemQuantity(productId, quantity));
    }

    @Test
    void shouldRejectNullProduct() {
        Cart cart = new Cart("user-1");

        assertThrows(DomainValidationException.class, () -> cart.addItem(null, 1));
        assertThrows(DomainValidationException.class, () -> cart.setItemQuantity(null, 1));
    }

    @Test
    void shouldRenewTimeToLiveOnEveryChange() {
        Cart cart = new Cart("user-1");
        long initialTtl = cart.getTtlSeconds();

        cart.addItem(productId, 1);

        assertEquals(initialTtl, cart.getTtlSeconds());
        assertTrue(cart.getTtlSeconds() > 0);
    }
}
