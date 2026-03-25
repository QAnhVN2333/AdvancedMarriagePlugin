package com.lyrinth.advancedmarriage.database;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class DatabaseManagerJdbcPropertyTest {

    @Test
    void buildRemoteJdbcUrlShouldNotContainHardcodedMysqlQueryParameters() {
        // Create manager with minimal plugin dependency for pure URL builder testing.
        DatabaseManager manager = new DatabaseManager(mock(JavaPlugin.class));

        // Verify MySQL URL is now clean and driver options come from datasource properties.
        String jdbcUrl = manager.buildRemoteJdbcUrl(StorageType.MYSQL, "127.0.0.1:3306", "advancedmarriage");

        assertEquals("jdbc:mysql://127.0.0.1:3306/advancedmarriage", jdbcUrl);
    }

    @Test
    void resolveMySqlSslModeShouldMapFlagsToExpectedDriverMode() {
        // SSL disabled must always return DISABLED, regardless of certificate flag.
        DatabaseManager manager = new DatabaseManager(mock(JavaPlugin.class));
        assertEquals("DISABLED", manager.resolveMySqlSslMode(false, false));
        assertEquals("DISABLED", manager.resolveMySqlSslMode(false, true));

        // SSL enabled without certificate verification uses REQUIRED.
        assertEquals("REQUIRED", manager.resolveMySqlSslMode(true, false));

        // SSL enabled with certificate verification uses VERIFY_CA.
        assertEquals("VERIFY_CA", manager.resolveMySqlSslMode(true, true));
    }
}

