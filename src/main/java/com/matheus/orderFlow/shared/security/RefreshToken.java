package com.matheus.orderFlow.shared.security;

import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;
import org.springframework.data.redis.core.index.Indexed;

import java.util.UUID;

@RedisHash("refreshToken")
@Getter
class RefreshToken {

    @Id
    private String id;

    private UUID userId;

    @Indexed
    private String familyId;

    private boolean used;

    @TimeToLive
    private long ttlSeconds;

    protected RefreshToken() {
    }

    RefreshToken(String id, UUID userId, String familyId, long ttlSeconds) {
        this.id = id;
        this.userId = userId;
        this.familyId = familyId;
        this.used = false;
        this.ttlSeconds = ttlSeconds;
    }

    void markUsed() {
        this.used = true;
    }
}
