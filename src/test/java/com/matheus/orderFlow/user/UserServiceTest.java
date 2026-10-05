package com.matheus.orderFlow.user;

import com.matheus.orderFlow.shared.exception.DomainValidationException;
import com.matheus.orderFlow.shared.exception.NotFoundException;
import com.matheus.orderFlow.shared.exception.UserAlreadyExistsException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private static final String HASH = "$2a$04$hashqualquer";

    private UserDto dto() {
        return new UserDto("Matheus", "matheus@example.com", "senhaSegura1");
    }

    private User user() {
        return new User("Matheus", "matheus@example.com", HASH);
    }

    @Test
    void shouldCreateUserHashingThePassword() {
        when(userRepository.existsByEmail("matheus@example.com")).thenReturn(false);
        when(passwordEncoder.encode("senhaSegura1")).thenReturn(HASH);
        when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));

        UserResponse response = userService.createUser(dto());

        assertEquals("matheus@example.com", response.email());
        verify(passwordEncoder).encode("senhaSegura1");
    }

    @Test
    void shouldRejectDuplicatedEmail() {
        when(userRepository.existsByEmail("matheus@example.com")).thenReturn(true);

        assertThrows(UserAlreadyExistsException.class, () -> userService.createUser(dto()));

        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldNotHashThePasswordWhenTheEmailIsTaken() {
        when(userRepository.existsByEmail("matheus@example.com")).thenReturn(true);

        assertThrows(UserAlreadyExistsException.class, () -> userService.createUser(dto()));

        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void shouldRejectPasswordShorterThanTheMinimum() {
        when(userRepository.existsByEmail("matheus@example.com")).thenReturn(false);

        UserDto dto = new UserDto("Matheus", "matheus@example.com", "curta");

        assertThrows(DomainValidationException.class, () -> userService.createUser(dto));

        verifyNoInteractions(passwordEncoder);
        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldThrowNotFoundForUnknownUser() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> userService.getUser(id));
    }

    @Test
    void shouldUpdateName() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(user()));
        when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));

        UserResponse response = userService.updateName(id, "Matheus Souza");

        assertEquals("Matheus Souza", response.name());
    }

    @Test
    void shouldRejectUpdatingToAnEmailAlreadyTaken() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(user()));
        when(userRepository.existsByEmail("outro@example.com")).thenReturn(true);

        assertThrows(UserAlreadyExistsException.class,
                () -> userService.updateEmail(id, "outro@example.com"));

        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldAllowUpdatingToTheSameEmail() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(user()));
        when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));

        UserResponse response = userService.updateEmail(id, "matheus@example.com");

        assertEquals("matheus@example.com", response.email());
        verify(userRepository, never()).existsByEmail(any());
    }

    @Test
    void shouldHashTheNewPasswordWhenUpdatingIt() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(user()));
        when(passwordEncoder.encode("novaSenha123")).thenReturn("$2a$04$novohash");
        when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));

        userService.updatePassword(id, "novaSenha123");

        verify(passwordEncoder).encode("novaSenha123");
    }

    @Test
    void shouldRejectAWeakNewPassword() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(user()));

        assertThrows(DomainValidationException.class, () -> userService.updatePassword(id, "123"));

        verifyNoInteractions(passwordEncoder);
    }
}
