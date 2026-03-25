package com.lyrinth.advancedmarriage.service;

import com.lyrinth.advancedmarriage.database.DatabaseManager;
import com.lyrinth.advancedmarriage.model.HomeLocation;
import org.bukkit.Location;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class HomeService {
    private final DatabaseManager databaseManager;

    public HomeService(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void setHome(long marriageId, String serverId, Location location) throws SQLException {
        if (location.getWorld() == null) {
            throw new SQLException("Cannot set home: world is null.");
        }

        String worldName = location.getWorld().getName();

        try (Connection connection = databaseManager.getConnection()) {
            // Try update first to keep one record per marriage.
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE am_homes SET world_name = ?, server_id = ?, x = ?, y = ?, z = ?, yaw = ?, pitch = ? WHERE marriage_id = ?")) {
                update.setString(1, worldName);
                update.setString(2, serverId);
                update.setDouble(3, location.getX());
                update.setDouble(4, location.getY());
                update.setDouble(5, location.getZ());
                update.setFloat(6, location.getYaw());
                update.setFloat(7, location.getPitch());
                update.setLong(8, marriageId);
                int updated = update.executeUpdate();
                if (updated > 0) {
                    return;
                }
            }

            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO am_homes(marriage_id, world_name, server_id, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                insert.setLong(1, marriageId);
                insert.setString(2, worldName);
                insert.setString(3, serverId);
                insert.setDouble(4, location.getX());
                insert.setDouble(5, location.getY());
                insert.setDouble(6, location.getZ());
                insert.setFloat(7, location.getYaw());
                insert.setFloat(8, location.getPitch());
                insert.executeUpdate();
            }
        }
    }

    public Optional<HomeLocation> getHome(long marriageId) throws SQLException {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM am_homes WHERE marriage_id = ?")) {
            statement.setLong(1, marriageId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(new HomeLocation(
                        marriageId,
                        resultSet.getString("world_name"),
                        resultSet.getString("server_id"),
                        resultSet.getDouble("x"),
                        resultSet.getDouble("y"),
                        resultSet.getDouble("z"),
                        resultSet.getFloat("yaw"),
                        resultSet.getFloat("pitch")
                ));
            }
        }
    }

    public boolean deleteHome(long marriageId) throws SQLException {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM am_homes WHERE marriage_id = ?")) {
            statement.setLong(1, marriageId);
            return statement.executeUpdate() > 0;
        }
    }
}
