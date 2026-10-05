package com.matheus.orderFlow.user;

import com.matheus.orderFlow.shared.exception.DomainValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    private static final String HASH = "$2a$04$abcdefghijklmnopqrstuv";

    private User user() {
        return new User("Matheus", "matheus@example.com", HASH);
    }

    @Test
    void shouldCreateUserAsRegularUser() {
        User user = user();

        assertEquals("Matheus", user.getName());
        assertEquals("matheus@example.com", user.getEmail());
        assertEquals(HASH, user.getPasswordHash());
        assertEquals(UserRole.USER, user.getRole());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void shouldRejectBlankName(String name) {
        assertThrows(DomainValidationException.class,
                () -> new User(name, "matheus@example.com", HASH));
    }

    @Test
    void shouldRejectNameLongerThanTheColumn() {
        String name = "a".repeat(151);

        assertThrows(DomainValidationException.class,
                () -> new User(name, "matheus@example.com", HASH));
    }

    @ParameterizedTest
    @ValueSource(strings = {"sem-arroba", "@sem-local.com", "sem@dominio", "com espaco@a.com"})
    void shouldRejectInvalidEmail(String email) {
        assertThrows(DomainValidationException.class, () -> new User("Matheus", email, HASH));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void shouldRejectBlankPasswordHash(String passwordHash) {
        assertThrows(DomainValidationException.class,
                () -> new User("Matheus", "matheus@example.com", passwordHash));
    }

    @Test
    void shouldChangeName() {
        User user = user();

        user.changeName("Matheus Souza");

        assertEquals("Matheus Souza", user.getName());
    }

    @Test
    void shouldRejectBlankNameWhenChangingIt() {
        User user = user();

        assertThrows(DomainValidationException.class, () -> user.changeName(" "));
        assertEquals("Matheus", user.getName());
    }

    @Test
    void shouldChangeEmail() {
        User user = user();

        user.changeEmail("novo@example.com");

        assertEquals("novo@example.com", user.getEmail());
    }

    @Test
    void shouldRejectInvalidEmailWhenChangingIt() {
        User user = user();

        assertThrows(DomainValidationException.class, () -> user.changeEmail("invalido"));
        assertEquals("matheus@example.com", user.getEmail());
    }

    @Test
    void shouldChangePasswordHash() {
        User user = user();

        user.changePasswordHash("$2a$04$outrohashqualquervalido");

        assertEquals("$2a$04$outrohashqualquervalido", user.getPasswordHash());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"1234567", "curta"})
    void shouldRejectPasswordShorterThanTheMinimum(String rawPassword) {
        assertThrows(DomainValidationException.class,
                () -> User.validatePasswordPolicy(rawPassword));
    }

    @Test
    void shouldAcceptPasswordWithTheMinimumLength() {
        assertDoesNotThrow(() -> User.validatePasswordPolicy("12345678"));
    }
}
