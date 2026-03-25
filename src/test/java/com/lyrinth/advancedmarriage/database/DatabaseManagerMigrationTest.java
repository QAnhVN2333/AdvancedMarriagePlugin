package com.lyrinth.advancedmarriage.database;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatabaseManagerMigrationTest {

    @Test
    void shouldMigrateLegacyChestTableBeforeCreatingLockIndex() throws Exception {
        File tempDir = Files.createTempDirectory("advancedmarriage-db-migration-test").toFile();
        File databaseFile = new File(tempDir, "advancedmarriage.db");
        createLegacyChestSchema(databaseFile);

        JavaPlugin plugin = mock(JavaPlugin.class);
        FileConfiguration config = new YamlConfiguration();
        config.set("db.type", "sqlite");
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDataFolder()).thenReturn(tempDir);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("DatabaseManagerMigrationTest"));

        DatabaseManager manager = new DatabaseManager(plugin);
        try {
            // Connect triggers schema setup and migration.
            manager.connect();
            assertTrue(hasColumn(databaseFile, "am_chests", "lock_until"));
            assertTrue(hasColumn(databaseFile, "am_chests", "updated_at"));
            assertTrue(hasColumn(databaseFile, "am_chests", "last_saved_by"));
            assertTrue(hasIndex(databaseFile, "idx_am_chests_lock_until"));
        } finally {
            manager.close();
            deleteRecursively(tempDir);
        }
    }

    private void createLegacyChestSchema(File databaseFile) throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
             Statement statement = connection.createStatement()) {
            // Simulate pre-migration schema from older plugin versions.
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS am_chests ("
                    + "marriage_id BIGINT PRIMARY KEY,"
                    + "server_id VARCHAR(100) NOT NULL,"
                    + "size INT NOT NULL,"
                    + "data LONGTEXT"
                    + ")");
        }
    }

    private boolean hasColumn(File databaseFile, String tableName, String columnName) throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
             PreparedStatement statement = connection.prepareStatement("PRAGMA table_info(" + tableName + ")");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                if (columnName.equalsIgnoreCase(resultSet.getString("name"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private boolean hasIndex(File databaseFile, String indexName) throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
             PreparedStatement statement = connection.prepareStatement("PRAGMA index_list('am_chests')");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                if (indexName.equalsIgnoreCase(resultSet.getString("name"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private void deleteRecursively(File file) {
        if (!file.exists()) {
            return;
        }

        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }

        try {
            Files.deleteIfExists(file.toPath());
        } catch (IOException exception) {
            fail("Failed to clean up test file: " + file.getAbsolutePath() + " - " + exception.getMessage());
        }
    }
}

