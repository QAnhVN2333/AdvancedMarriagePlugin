package com.lyrinth.advancedmarriage.model;

import java.time.Instant;
import java.util.UUID;

public class MarriageRequest {
    private final UUID sender;
    private final UUID receiver;
    private final Instant expiresAt;

    public MarriageRequest(UUID sender, UUID receiver, Instant expiresAt) {
        this.sender = sender;
        this.receiver = receiver;
        this.expiresAt = expiresAt;
    }

    public UUID getSender() {
        return sender;
    }

    public UUID getReceiver() {
        return receiver;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}

