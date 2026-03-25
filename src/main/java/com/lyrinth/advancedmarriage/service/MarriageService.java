package com.lyrinth.advancedmarriage.service;

import com.lyrinth.advancedmarriage.database.DatabaseManager;
import com.lyrinth.advancedmarriage.model.Marriage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MarriageService {
    private final DatabaseManager databaseManager;
    private final RequestManager requestManager;
    private final Map<UUID, Marriage> marriageByPlayer = new ConcurrentHashMap<>();
    private final Map<Long, Marriage> marriageById = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> divorceCooldown = new ConcurrentHashMap<>();
    private final Object cacheLock = new Object();

    public MarriageService(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        this.requestManager = new RequestManager();
    }

    public void loadMarriages() throws SQLException {
        synchronized (cacheLock) {
            marriageByPlayer.clear();
            marriageById.clear();

            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement statement = connection.prepareStatement("SELECT * FROM am_marriages");
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Marriage marriage = new Marriage(
                            resultSet.getLong("id"),
                            UUID.fromString(resultSet.getString("player_a_uuid")),
                            UUID.fromString(resultSet.getString("player_b_uuid")),
                            Instant.ofEpochMilli(resultSet.getLong("married_at")),
                            resultSet.getString("server_id"),
                            resultSet.getBoolean("pvp_enabled")
                    );
                    marriageById.put(marriage.getId(), marriage);
                    marriageByPlayer.put(marriage.getPlayerA(), marriage);
                    marriageByPlayer.put(marriage.getPlayerB(), marriage);
                }
            }
        }
    }

    public boolean isMarried(UUID playerUuid) {
        return marriageByPlayer.containsKey(playerUuid);
    }

    public Optional<Marriage> getMarriage(UUID playerUuid) {
        return Optional.ofNullable(marriageByPlayer.get(playerUuid));
    }

    public List<Marriage> getAllMarriages() {
        List<Marriage> marriages = new ArrayList<>(marriageById.values());
        marriages.sort(Comparator.comparing(Marriage::getMarriedAt).reversed());
        return marriages;
    }

    public Marriage createMarriage(UUID playerA, UUID playerB, String serverId) throws SQLException {
        long now = System.currentTimeMillis();

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO am_marriages(player_a_uuid, player_b_uuid, married_at, server_id, pvp_enabled) VALUES (?, ?, ?, ?, ?)",
                     PreparedStatement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, playerA.toString());
            statement.setString(2, playerB.toString());
            statement.setLong(3, now);
            statement.setString(4, serverId);
            statement.setBoolean(5, true);
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Failed to create marriage: no generated key.");
                }
                long id = keys.getLong(1);
                Marriage marriage = new Marriage(id, playerA, playerB, Instant.ofEpochMilli(now), serverId, true);
                synchronized (cacheLock) {
                    marriageById.put(id, marriage);
                    marriageByPlayer.put(playerA, marriage);
                    marriageByPlayer.put(playerB, marriage);
                }
                return marriage;
            }
        }
    }

    public boolean divorce(UUID playerUuid) throws SQLException {
        Marriage marriage = marriageByPlayer.get(playerUuid);
        if (marriage == null) {
            return false;
        }

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement deleteHome = connection.prepareStatement("DELETE FROM am_homes WHERE marriage_id = ?");
             PreparedStatement deleteChest = connection.prepareStatement("DELETE FROM am_chests WHERE marriage_id = ?");
             PreparedStatement deleteMarriage = connection.prepareStatement("DELETE FROM am_marriages WHERE id = ?")) {
            deleteHome.setLong(1, marriage.getId());
            deleteHome.executeUpdate();

            deleteChest.setLong(1, marriage.getId());
            deleteChest.executeUpdate();

            deleteMarriage.setLong(1, marriage.getId());
            int changed = deleteMarriage.executeUpdate();
            if (changed > 0) {
                synchronized (cacheLock) {
                    marriageByPlayer.remove(marriage.getPlayerA());
                    marriageByPlayer.remove(marriage.getPlayerB());
                    marriageById.remove(marriage.getId());
                }
                return true;
            }
        }
        return false;
    }

    public Optional<UUID> getPartner(UUID playerUuid) {
        Marriage marriage = marriageByPlayer.get(playerUuid);
        if (marriage == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(marriage.getPartner(playerUuid));
    }

    public void setPvpEnabled(Marriage marriage, boolean enabled) throws SQLException {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE am_marriages SET pvp_enabled = ? WHERE id = ?")) {
            statement.setBoolean(1, enabled);
            statement.setLong(2, marriage.getId());
            statement.executeUpdate();
            marriage.setPvpEnabled(enabled);
        }
    }

    public RequestManager getRequestManager() {
        return requestManager;
    }

    public void addDivorceCooldown(UUID playerUuid, int minutes) {
        divorceCooldown.put(playerUuid, Instant.now().plusSeconds(minutes * 60L));
    }

    public boolean isInDivorceCooldown(UUID playerUuid) {
        Instant expires = divorceCooldown.get(playerUuid);
        if (expires == null) {
            return false;
        }
        if (Instant.now().isAfter(expires)) {
            divorceCooldown.remove(playerUuid);
            return false;
        }
        return true;
    }
}

