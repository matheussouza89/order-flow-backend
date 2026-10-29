package com.matheus.orderFlow.shared.security;

import com.matheus.orderFlow.shared.exception.InvalidLoginException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class RefreshTokenService {
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Duration expiration;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${orderflow.security.jwt.refresh-expiration}") Duration expiration) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.expiration = expiration;
    }

    public String issue(UUID userId) {
        return issueInFamily(userId, UUID.randomUUID().toString());
    }

    public RotatedToken rotate(String rawToken) {
        RefreshToken stored = findOrReject(rawToken);

        if (stored.isUsed()) {
            log.warn("Refresh token reuse detected: revoking family of user {}", stored.getUserId());
            revokeFamily(stored.getFamilyId());
            throw new InvalidLoginException("Invalid refresh token");
        }

        stored.markUsed();
        refreshTokenRepository.save(stored);

        return new RotatedToken(
                stored.getUserId(),
                issueInFamily(stored.getUserId(), stored.getFamilyId()));
    }

    public void revoke(String rawToken) {
        findStored(rawToken).ifPresent(stored -> revokeFamily(stored.getFamilyId()));
    }

    public Duration getExpiration() {
        return expiration;
    }

    private String issueInFamily(UUID userId, String familyId) {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);

        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        refreshTokenRepository.save(
                new RefreshToken(hash(rawToken), userId, familyId, expiration.toSeconds()));

        return rawToken;
    }

    private RefreshToken findOrReject(String rawToken) {
        return findStored(rawToken)
                .orElseThrow(() -> new InvalidLoginException("Invalid refresh token"));
    }

    private Optional<RefreshToken> findStored(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }

        return refreshTokenRepository.findById(hash(rawToken));
    }

    private void revokeFamily(String familyId) {
        List<RefreshToken> family = refreshTokenRepository.findByFamilyId(familyId);

        refreshTokenRepository.deleteAll(family);

        log.info("Refresh token family revoked: tokens={}", family.size());
    }

    private String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));

            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
