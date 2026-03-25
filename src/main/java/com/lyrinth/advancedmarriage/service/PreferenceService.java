package com.lyrinth.advancedmarriage.service;

import com.lyrinth.advancedmarriage.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class PreferenceService {
    private final DatabaseManager databaseManager;

    public PreferenceService(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public boolean isPartnerChatEnabled(UUID playerUuid) {
        return getBoolean(playerUuid, "partner_chat_enabled", false);
    }

    public boolean isTpAllowPartner(UUID playerUuid) {
        return getBoolean(playerUuid, "tp_allow_partner", true);
    }

    public void setPartnerChatEnabled(UUID playerUuid, boolean enabled) {
        setBoolean(playerUuid, "partner_chat_enabled", enabled);
    }

    public void setTpAllowPartner(UUID playerUuid, boolean enabled) {
        setBoolean(playerUuid, "tp_allow_partner", enabled);
    }

    public UUID getDisplayUuid(UUID playerUuid) {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT display_uuid FROM am_preferences WHERE player_uuid = ?")) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                String raw = resultSet.getString("display_uuid");
                return raw == null ? null : UUID.fromString(raw);
            }
        } catch (SQLException ex) {
            return null;
        }
    }

    public void setDisplayUuid(UUID playerUuid, UUID displayUuid) {
        upsertPreference(playerUuid);
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE am_preferences SET display_uuid = ? WHERE player_uuid = ?")) {
            statement.setString(1, displayUuid == null ? null : displayUuid.toString());
            statement.setString(2, playerUuid.toString());
            statement.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    private boolean getBoolean(UUID playerUuid, String column, boolean defaultValue) {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT " + column + " FROM am_preferences WHERE player_uuid = ?")) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    upsertPreference(playerUuid);
                    return defaultValue;
                }
                return resultSet.getBoolean(column);
            }
        } catch (SQLException ex) {
            return defaultValue;
        }
    }

    private void setBoolean(UUID playerUuid, String column, boolean value) {
        upsertPreference(playerUuid);
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE am_preferences SET " + column + " = ? WHERE player_uuid = ?")) {
            statement.setBoolean(1, value);
            statement.setString(2, playerUuid.toString());
            statement.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    private void upsertPreference(UUID playerUuid) {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO am_preferences(player_uuid) VALUES (?) ON CONFLICT(player_uuid) DO NOTHING")) {
            statement.setString(1, playerUuid.toString());
            statement.executeUpdate();
        } catch (SQLException ignored) {
            // Fallback query for non-Postgres syntax support.
            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "INSERT IGNORE INTO am_preferences(player_uuid) VALUES (?)")) {
                statement.setString(1, playerUuid.toString());
                statement.executeUpdate();
            } catch (SQLException ignoredAgain) {
            }
        }
    }
}
