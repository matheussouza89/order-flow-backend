package com.matheus.orderFlow.user;

import com.matheus.orderFlow.contracts.user.UserEmailChangedEvent;
import com.matheus.orderFlow.contracts.user.UserFacts;
import com.matheus.orderFlow.contracts.user.UserRegisteredEvent;
import com.matheus.orderFlow.shared.exception.DomainValidationException;
import com.matheus.orderFlow.shared.exception.NotFoundException;
import com.matheus.orderFlow.shared.outbox.OutboxWriter;
import com.matheus.orderFlow.shared.web.PageResponse;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
    private static final String ABSENT_USER_PLACEHOLDER = "there-is-no-user-with-this-email";
    private static final int MAX_EXPORT_SIZE = 500;
    private static final Sort REGISTRATION_ORDER = Sort.by("createdAt", "id");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokenService;
    private final OutboxWriter outboxWriter;

    private String absentUserHash;

    @PostConstruct
    void prepareAbsentUserHash() {
        absentUserHash = passwordEncoder.encode(ABSENT_USER_PLACEHOLDER);
    }

    @Transactional
    public UserResponse createUser(UserDto dto) {
        requireEmailAvailable(dto.email());
        User.validatePasswordPolicy(dto.password());

        User newUser = new User(dto.name(), dto.email(), passwordEncoder.encode(dto.password()));

        log.info("Creating user: {}", newUser.getEmail());

        User created = userRepository.saveAndFlush(newUser);
        outboxWriter.record(UserFacts.REGISTERED_KEY,
                new UserRegisteredEvent(created.getId(), created.getEmail(), created.getUpdatedAt()));

        return UserResponse.from(created);
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(UUID id) {
        return UserResponse.from(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<UserExportResponse> export(int page, int size) {
        if (page < 0) {
            throw new DomainValidationException("page", "Page must not be negative");
        }
        if (size < 1 || size > MAX_EXPORT_SIZE) {
            throw new DomainValidationException("size", "Size must be between 1 and " + MAX_EXPORT_SIZE);
        }

        return PageResponse.of(userRepository.findAll(PageRequest.of(page, size, REGISTRATION_ORDER))
                .map(UserExportResponse::from));
    }

    @Transactional
    public UserResponse updateName(UUID id, String newName) {
        User user = findOrThrow(id);
        user.changeName(newName);

        log.info("Updating user name: id={}", id);

        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public UserResponse updateEmail(UUID id, String newEmail) {
        User user = findOrThrow(id);
        boolean changed = !user.getEmail().equals(newEmail);

        if (changed) {
            requireEmailAvailable(newEmail);
        }

        user.changeEmail(newEmail);

        log.info("Updating user email: id={}", id);

        User updated = userRepository.saveAndFlush(user);

        if (changed) {
            outboxWriter.record(UserFacts.EMAIL_CHANGED_KEY,
                    new UserEmailChangedEvent(updated.getId(), updated.getEmail(), updated.getUpdatedAt()));
        }

        return UserResponse.from(updated);
    }

    @Transactional
    public UserResponse updatePassword(UUID id, String newPassword) {
        User user = findOrThrow(id);

        User.validatePasswordPolicy(newPassword);
        user.changePasswordHash(passwordEncoder.encode(newPassword));

        log.info("Updating user password: id={}", id);

        return UserResponse.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public TokenResponse login(String email, String password) {
        User user = userRepository.findByEmail(email).orElse(null);

        String hashToCheck = user == null ? absentUserHash : user.getPasswordHash();

        if (!passwordEncoder.matches(password, hashToCheck) || user == null) {
            log.warn("Failed login attempt");
            throw new InvalidLoginException("Invalid credentials");
        }

        log.info("User logged in: id={}", user.getId());

        return tokensFor(user, refreshTokenService.issue(user.getId()));
    }

    @Transactional(readOnly = true)
    public TokenResponse refresh(String refreshToken) {
        RotatedToken rotated = refreshTokenService.rotate(refreshToken);

        User user = userRepository.findById(rotated.userId())
                .orElseThrow(() -> new InvalidLoginException("Invalid refresh token"));

        log.info("Access token refreshed: id={}", user.getId());

        return tokensFor(user, rotated.refreshToken());
    }

    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    private TokenResponse tokensFor(User user, String refreshToken) {
        String accessToken = tokenService.generateToken(user.getId(), user.getRole().name());

        return TokenResponse.bearer(
                accessToken, tokenService.getExpiration().toSeconds(), refreshToken);
    }

    private User findOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(id));
    }

    private void requireEmailAvailable(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new UserAlreadyExistsException("Email " + email + " is already registered");
        }
    }
}
