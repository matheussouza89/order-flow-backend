package com.matheus.orderFlow.contact;

import com.matheus.orderFlow.shared.exception.DomainValidationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "contacts")
@Getter
class Contact {
    private static final int EMAIL_MAX_LENGTH = 150;

    @Id
    private UUID userId;

    @Column(nullable = false, length = EMAIL_MAX_LENGTH)
    private String email;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Contact() {
    }

    Contact(UUID userId, String email, Instant updatedAt) {
        if (userId == null) {
            throw new DomainValidationException("userId", "Contact must belong to a user");
        }

        validate(email, updatedAt);

        this.userId = userId;
        this.email = email;
        this.updatedAt = updatedAt;
    }

    boolean replaceIfNewer(String email, Instant updatedAt) {
        validate(email, updatedAt);

        if (!updatedAt.isAfter(this.updatedAt)) {
            return false;
        }

        this.email = email;
        this.updatedAt = updatedAt;
        return true;
    }

    private static void validate(String email, Instant updatedAt) {
        if (email == null || email.isBlank()) {
            throw new DomainValidationException("email", "Email cannot be blank");
        }

        if (email.length() > EMAIL_MAX_LENGTH) {
            throw new DomainValidationException("email",
                    "Email must have at most " + EMAIL_MAX_LENGTH + " characters");
        }

        if (updatedAt == null) {
            throw new DomainValidationException("updatedAt", "The moment of the change is required");
        }
    }
}
