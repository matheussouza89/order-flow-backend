package com.matheus.orderFlow.user;

import com.matheus.orderFlow.shared.exception.DomainValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private static final String EMAIL = "admin@orderflow.local";
    private static final String PASSWORD = "senhaDoAdmin1";

    private AdminBootstrap bootstrap(String email, String password) {
        return new AdminBootstrap(userRepository, passwordEncoder, "Administrator", email, password);
    }

    private AdminBootstrap bootstrap() {
        return bootstrap(EMAIL, PASSWORD);
    }

    @Test
    void shouldCreateTheAdministratorWhenNoneExists() {
        when(userRepository.existsByRole(UserRole.ADMIN)).thenReturn(false);
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("$2a$04$hash");

        bootstrap().run(null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        User created = captor.getValue();
        assertEquals(UserRole.ADMIN, created.getRole());
        assertEquals(EMAIL, created.getEmail());
        assertEquals("$2a$04$hash", created.getPasswordHash());
    }

    @Test
    void shouldDoNothingWhenAnAdministratorAlreadyExists() {
        when(userRepository.existsByRole(UserRole.ADMIN)).thenReturn(true);

        bootstrap().run(null);

        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void shouldBeIdempotentAcrossRestarts() {
        when(userRepository.existsByRole(UserRole.ADMIN)).thenReturn(false, true);
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("$2a$04$hash");

        AdminBootstrap bootstrap = bootstrap();
        bootstrap.run(null);
        bootstrap.run(null);

        verify(userRepository, times(1)).save(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void shouldSkipWhenThePasswordIsNotConfigured(String password) {
        when(userRepository.existsByRole(UserRole.ADMIN)).thenReturn(false);

        bootstrap(EMAIL, password).run(null);

        verify(userRepository, never()).save(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    void shouldSkipWhenTheEmailIsNotConfigured(String email) {
        when(userRepository.existsByRole(UserRole.ADMIN)).thenReturn(false);

        bootstrap(email, PASSWORD).run(null);

        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldNotOverwriteARegularUserHoldingTheSameEmail() {
        when(userRepository.existsByRole(UserRole.ADMIN)).thenReturn(false);
        when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

        bootstrap().run(null);

        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void shouldRejectAConfiguredPasswordThatBreaksThePolicy() {
        when(userRepository.existsByRole(UserRole.ADMIN)).thenReturn(false);
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);

        assertThrows(DomainValidationException.class, () -> bootstrap(EMAIL, "123").run(null));

        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldNeverStoreTheConfiguredPasswordAsIs() {
        when(userRepository.existsByRole(UserRole.ADMIN)).thenReturn(false);
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("$2a$04$hash");

        bootstrap().run(null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        assertNotEquals(PASSWORD, captor.getValue().getPasswordHash());
    }
}
