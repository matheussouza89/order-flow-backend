package com.matheus.orderFlow.user;

import com.matheus.orderFlow.shared.exception.InvalidLoginException;
import com.matheus.orderFlow.shared.exception.NotFoundException;
import com.matheus.orderFlow.shared.exception.UserAlreadyExistsException;
import com.matheus.orderFlow.shared.security.TokenService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

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

        return UserResponse.from(userRepository.save(newUser));
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(UUID id) {
        return UserResponse.from(findOrThrow(id));
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

        if (!user.getEmail().equals(newEmail)) {
            requireEmailAvailable(newEmail);
        }

        user.changeEmail(newEmail);

        log.info("Updating user email: id={}", id);

        return UserResponse.from(userRepository.save(user));
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

        String token = tokenService.generateToken(user.getId(), user.getRole().name());

        return TokenResponse.bearer(token, tokenService.getExpiration().toSeconds());
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
