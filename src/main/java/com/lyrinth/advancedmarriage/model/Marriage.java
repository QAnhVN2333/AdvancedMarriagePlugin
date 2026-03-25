package com.lyrinth.advancedmarriage.model;

import java.time.Instant;
import java.util.UUID;

public class Marriage {
    private final long id;
    private final UUID playerA;
    private final UUID playerB;
    private final Instant marriedAt;
    private final String serverId;
    private boolean pvpEnabled;

    public Marriage(long id, UUID playerA, UUID playerB, Instant marriedAt, String serverId, boolean pvpEnabled) {
        this.id = id;
        this.playerA = playerA;
        this.playerB = playerB;
        this.marriedAt = marriedAt;
        this.serverId = serverId;
        this.pvpEnabled = pvpEnabled;
    }

    public long getId() {
        return id;
    }

    public UUID getPlayerA() {
        return playerA;
    }

    public UUID getPlayerB() {
        return playerB;
    }

    public Instant getMarriedAt() {
        return marriedAt;
    }

    public String getServerId() {
        return serverId;
    }

    public boolean isPvpEnabled() {
        return pvpEnabled;
    }

    public void setPvpEnabled(boolean pvpEnabled) {
        this.pvpEnabled = pvpEnabled;
    }

    public UUID getPartner(UUID playerUuid) {
        if (playerA.equals(playerUuid)) {
            return playerB;
        }
        if (playerB.equals(playerUuid)) {
            return playerA;
        }
        return null;
    }

    public boolean includes(UUID playerUuid) {
        return playerA.equals(playerUuid) || playerB.equals(playerUuid);
    }
}

