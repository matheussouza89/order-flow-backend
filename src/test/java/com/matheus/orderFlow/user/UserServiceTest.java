package com.matheus.orderFlow.user;

import com.matheus.orderFlow.shared.exception.DomainValidationException;
import com.matheus.orderFlow.shared.exception.InvalidLoginException;
import com.matheus.orderFlow.shared.security.RefreshTokenService;
import com.matheus.orderFlow.shared.security.RotatedToken;
import com.matheus.orderFlow.shared.security.TokenService;
import com.matheus.orderFlow.shared.exception.NotFoundException;
import com.matheus.orderFlow.shared.exception.UserAlreadyExistsException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenService tokenService;

    @Mock
    private RefreshTokenService refreshTokenService;

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
    void shouldReturnABearerTokenOnSuccessfulLogin() {
        User user = user();
        when(userRepository.findByEmail("matheus@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("senhaSegura1", HASH)).thenReturn(true);
        when(tokenService.generateToken(any(), eq("USER"))).thenReturn("token-assinado");
        when(tokenService.getExpiration()).thenReturn(Duration.ofMinutes(15));
        when(refreshTokenService.issue(any())).thenReturn("refresh-opaco");

        TokenResponse response = userService.login("matheus@example.com", "senhaSegura1");

        assertEquals("token-assinado", response.token());
        assertEquals("Bearer", response.type());
        assertEquals(900, response.expiresIn());
        assertEquals("refresh-opaco", response.refreshToken());
    }

    @Test
    void shouldIssueANewPairOnRefresh() {
        UUID userId = UUID.randomUUID();
        when(refreshTokenService.rotate("refresh-antigo"))
                .thenReturn(new RotatedToken(userId, "refresh-novo"));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user()));
        when(tokenService.generateToken(any(), eq("USER"))).thenReturn("novo-access");
        when(tokenService.getExpiration()).thenReturn(Duration.ofMinutes(15));

        TokenResponse response = userService.refresh("refresh-antigo");

        assertEquals("novo-access", response.token());
        assertEquals("refresh-novo", response.refreshToken());
    }

    @Test
    void shouldRejectRefreshWhenTheUserNoLongerExists() {
        UUID userId = UUID.randomUUID();
        when(refreshTokenService.rotate("refresh-antigo"))
                .thenReturn(new RotatedToken(userId, "refresh-novo"));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(InvalidLoginException.class, () -> userService.refresh("refresh-antigo"));

        verifyNoInteractions(tokenService);
    }

    @Test
    void shouldDelegateLogoutToTheRefreshTokenService() {
        userService.logout("refresh-qualquer");

        verify(refreshTokenService).revoke("refresh-qualquer");
    }

    @Test
    void shouldRejectLoginWithWrongPassword() {
        when(userRepository.findByEmail("matheus@example.com")).thenReturn(Optional.of(user()));
        when(passwordEncoder.matches(eq("errada"), any())).thenReturn(false);

        assertThrows(InvalidLoginException.class,
                () -> userService.login("matheus@example.com", "errada"));

        verifyNoInteractions(tokenService);
    }

    @Test
    void shouldRejectLoginWithUnknownEmail() {
        when(userRepository.findByEmail("ninguem@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.matches(eq("qualquer"), any())).thenReturn(false);

        assertThrows(InvalidLoginException.class,
                () -> userService.login("ninguem@example.com", "qualquer"));

        verifyNoInteractions(tokenService);
    }

    @Test
    void shouldHashCheckEvenWhenTheEmailDoesNotExist() {
        userService.prepareAbsentUserHash();
        when(userRepository.findByEmail("ninguem@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.matches(eq("qualquer"), any())).thenReturn(false);

        assertThrows(InvalidLoginException.class,
                () -> userService.login("ninguem@example.com", "qualquer"));

        verify(passwordEncoder).matches(eq("qualquer"), any());
    }

    @Test
    void shouldBuildTheAbsentUserHashWithTheConfiguredEncoder() {
        when(passwordEncoder.encode(any())).thenReturn("$2a$04$hashdescartavel");

        userService.prepareAbsentUserHash();

        verify(passwordEncoder).encode(any());
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
