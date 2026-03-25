package com.lyrinth.advancedmarriage.service;

import com.lyrinth.advancedmarriage.database.DatabaseManager;
import com.lyrinth.advancedmarriage.gui.SharedChestHolder;
import com.lyrinth.advancedmarriage.util.ItemSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

public class ChestService {
    private final DatabaseManager databaseManager;

    public ChestService(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public Optional<ChestLease> tryAcquireLease(
            long marriageId,
            String serverId,
            int defaultSize,
            long ttlMillis,
            boolean createIfMissing
    ) throws SQLException {
        long now = System.currentTimeMillis();
        long lockUntil = now + Math.max(1000L, ttlMillis);
        String token = UUID.randomUUID().toString();

        try (Connection connection = databaseManager.getConnection()) {
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE am_chests SET lock_owner = ?, lock_token = ?, lock_until = ?, updated_at = ? "
                            + "WHERE marriage_id = ? AND (lock_until IS NULL OR lock_until < ? OR lock_owner = ?)")) {
                update.setString(1, serverId);
                update.setString(2, token);
                update.setLong(3, lockUntil);
                update.setLong(4, now);
                update.setLong(5, marriageId);
                update.setLong(6, now);
                update.setString(7, serverId);

                int changed = update.executeUpdate();
                if (changed > 0) {
                    return Optional.of(new ChestLease(token, lockUntil));
                }
            }

            if (!createIfMissing) {
                return Optional.empty();
            }

            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO am_chests(marriage_id, server_id, size, data, version, lock_owner, lock_token, lock_until, updated_at, last_saved_by) "
                            + "VALUES (?, ?, ?, ?, 0, ?, ?, ?, ?, ?)")) {
                insert.setLong(1, marriageId);
                insert.setString(2, serverId);
                insert.setInt(3, normalizeChestSize(defaultSize, 36));
                insert.setString(4, null);
                insert.setString(5, serverId);
                insert.setString(6, token);
                insert.setLong(7, lockUntil);
                insert.setLong(8, now);
                insert.setString(9, serverId);
                insert.executeUpdate();
                return Optional.of(new ChestLease(token, lockUntil));
            } catch (SQLException ex) {
                // Another server may have inserted/acquired at the same time.
                return Optional.empty();
            }
        }
    }

    public boolean renewLease(long marriageId, String serverId, String lockToken, long ttlMillis) throws SQLException {
        long now = System.currentTimeMillis();
        long lockUntil = now + Math.max(1000L, ttlMillis);

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE am_chests SET lock_until = ?, updated_at = ? "
                             + "WHERE marriage_id = ? AND lock_owner = ? AND lock_token = ? AND lock_until >= ?")) {
            statement.setLong(1, lockUntil);
            statement.setLong(2, now);
            statement.setLong(3, marriageId);
            statement.setString(4, serverId);
            statement.setString(5, lockToken);
            statement.setLong(6, now);
            return statement.executeUpdate() > 0;
        }
    }

    public void releaseLease(long marriageId, String lockToken) throws SQLException {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE am_chests SET lock_owner = NULL, lock_token = NULL, lock_until = NULL, updated_at = ? "
                             + "WHERE marriage_id = ? AND lock_token = ?")) {
            statement.setLong(1, System.currentTimeMillis());
            statement.setLong(2, marriageId);
            statement.setString(3, lockToken);
            statement.executeUpdate();
        }
    }

    public ChestSnapshot loadInventorySnapshot(long marriageId, String title, int defaultSize, String requiredLockToken)
            throws SQLException, IOException {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT size, data, version, lock_token FROM am_chests WHERE marriage_id = ?")) {
            statement.setLong(1, marriageId);
            try (ResultSet resultSet = statement.executeQuery()) {
                int inventorySize = normalizeChestSize(defaultSize, 36);
                String data = null;
                long version = 0L;

                if (resultSet.next()) {
                    String lockToken = resultSet.getString("lock_token");
                    if (requiredLockToken != null && (lockToken == null || !requiredLockToken.equals(lockToken))) {
                        throw new SQLException("Lost shared chest lease before inventory load.");
                    }

                    int storedSize = resultSet.getInt("size");
                    inventorySize = normalizeChestSize(storedSize, inventorySize);
                    data = resultSet.getString("data");
                    version = resultSet.getLong("version");
                }

                Inventory inventory = createInventory(marriageId, title, inventorySize, data);
                return new ChestSnapshot(inventory, version);
            }
        }
    }

    public OptionalLong saveWithVersion(
            long marriageId,
            String serverId,
            Inventory inventory,
            long expectedVersion,
            String lockToken
    ) throws SQLException, IOException {
        String encoded = ItemSerializer.toBase64(inventory.getContents());

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE am_chests SET server_id = ?, size = ?, data = ?, version = version + 1, "
                             + "updated_at = ?, last_saved_by = ? WHERE marriage_id = ? AND version = ? AND lock_token = ?")) {
            update.setString(1, serverId);
            update.setInt(2, inventory.getSize());
            update.setString(3, encoded);
            update.setLong(4, System.currentTimeMillis());
            update.setString(5, serverId);
            update.setLong(6, marriageId);
            update.setLong(7, expectedVersion);
            update.setString(8, lockToken);

            int changed = update.executeUpdate();
            if (changed <= 0) {
                return OptionalLong.empty();
            }
            return OptionalLong.of(expectedVersion + 1L);
        }
    }

    public Inventory loadInventory(long marriageId, String title, int defaultSize) throws SQLException, IOException {
        return loadInventorySnapshot(marriageId, title, defaultSize, null).inventory();
    }

    public String getChestServerId(long marriageId) throws SQLException {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT server_id FROM am_chests WHERE marriage_id = ?")) {
            statement.setLong(1, marriageId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return resultSet.getString("server_id");
            }
        }
    }

    public void saveInventory(long marriageId, String serverId, Inventory inventory) throws SQLException, IOException {
        String encoded = ItemSerializer.toBase64(inventory.getContents());

        try (Connection connection = databaseManager.getConnection()) {
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE am_chests SET server_id = ?, size = ?, data = ?, updated_at = ?, last_saved_by = ? WHERE marriage_id = ?")) {
                update.setString(1, serverId);
                update.setInt(2, inventory.getSize());
                update.setString(3, encoded);
                update.setLong(4, System.currentTimeMillis());
                update.setString(5, serverId);
                update.setLong(6, marriageId);
                int changed = update.executeUpdate();
                if (changed > 0) {
                    return;
                }
            }

            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO am_chests(marriage_id, server_id, size, data, version, updated_at, last_saved_by) "
                            + "VALUES (?, ?, ?, ?, 0, ?, ?)")) {
                insert.setLong(1, marriageId);
                insert.setString(2, serverId);
                insert.setInt(3, inventory.getSize());
                insert.setString(4, encoded);
                insert.setLong(5, System.currentTimeMillis());
                insert.setString(6, serverId);
                insert.executeUpdate();
            }
        }
    }

    public boolean isEmpty(long marriageId) throws SQLException, IOException {
        return isInventoryEmpty(loadInventory(marriageId, "Temporary", 54));
    }

    public boolean isEmptyUnderLease(long marriageId, String lockToken, int defaultSize) throws SQLException, IOException {
        ChestSnapshot snapshot = loadInventorySnapshot(marriageId, "Temporary", defaultSize, lockToken);
        return isInventoryEmpty(snapshot.inventory());
    }

    private boolean isInventoryEmpty(Inventory inventory) {
        // Check every slot to ensure there is no real item left.
        for (ItemStack item : inventory.getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                return false;
            }
        }
        return true;
    }

    private Inventory createInventory(long marriageId, String title, int inventorySize, String data) throws IOException {
        SharedChestHolder holder = new SharedChestHolder(marriageId);
        Inventory inventory = Bukkit.createInventory(holder, inventorySize, title);
        holder.setInventory(inventory);

        if (data == null || data.isBlank()) {
            return inventory;
        }

        // Copy only up to current inventory size to avoid out-of-range data issues.
        ItemStack[] stored = ItemSerializer.fromBase64(data, inventorySize);
        ItemStack[] contents = new ItemStack[inventorySize];
        System.arraycopy(stored, 0, contents, 0, Math.min(stored.length, contents.length));
        inventory.setContents(contents);
        return inventory;
    }

    private int normalizeChestSize(int candidateSize, int fallbackSize) {
        int normalizedFallback = normalizeChestSizeInternal(fallbackSize);
        int normalizedCandidate = normalizeChestSizeInternal(candidateSize);
        return normalizedCandidate <= 0 ? normalizedFallback : normalizedCandidate;
    }

    private int normalizeChestSizeInternal(int size) {
        if (size < 27 || size > 54 || size % 9 != 0) {
            return -1;
        }
        return size;
    }

    public record ChestLease(String token, long lockUntilMillis) {
    }

    public record ChestSnapshot(Inventory inventory, long version) {
    }
}
