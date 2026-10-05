package com.matheus.orderFlow.user;

import com.matheus.orderFlow.shared.exception.DomainValidationException;
import jakarta.persistence.*;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(
    name = "users"
)
@EntityListeners(AuditingEntityListener.class)
@Getter
class User {
    private static final int NAME_MAX_LENGTH = 150;
    private static final int EMAIL_MAX_LENGTH = 150;
    private static final int PASSWORD_MIN_LENGTH = 8;
    private static final int ROLE_MAX_LENGTH = 50;
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    @Column(nullable = false, unique = true, length = EMAIL_MAX_LENGTH)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = ROLE_MAX_LENGTH)
    private UserRole role;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new DomainValidationException("name", "User name cannot be empty");
        }

        if (name.length() > NAME_MAX_LENGTH) {
            throw new DomainValidationException("name",
                    "User name cannot exceed " + NAME_MAX_LENGTH + " characters");
        }
    }

    private static void validateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new DomainValidationException("email", "User email cannot be empty");
        }

        if (email.length() > EMAIL_MAX_LENGTH) {
            throw new DomainValidationException("email",
                    "User email cannot exceed " + EMAIL_MAX_LENGTH + " characters");
        }

        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new DomainValidationException("email", "User email is not a valid address");
        }
    }

    private static void validatePasswordHash(String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new DomainValidationException("password", "User password cannot be empty");
        }
    }

    static void validatePasswordPolicy(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new DomainValidationException("password", "User password cannot be empty");
        }

        if (rawPassword.length() < PASSWORD_MIN_LENGTH) {
            throw new DomainValidationException("password",
                    "User password must have at least " + PASSWORD_MIN_LENGTH + " characters");
        }
    }

    protected User() {}

    User(String name, String email, String passwordHash) {
        validateName(name);
        validateEmail(email);
        validatePasswordHash(passwordHash);

        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = UserRole.USER;
    }

    void changeName(String name) {
        validateName(name);
        this.name = name;
    }

    void changeEmail(String email) {
        validateEmail(email);
        this.email = email;
    }

    void changePasswordHash(String passwordHash) {
        validatePasswordHash(passwordHash);
        this.passwordHash = passwordHash;
    }
}
