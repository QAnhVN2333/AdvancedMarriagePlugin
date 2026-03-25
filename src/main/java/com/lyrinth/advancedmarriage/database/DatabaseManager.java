package com.lyrinth.advancedmarriage.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {
    private final JavaPlugin plugin;
    private final Object lifecycleLock = new Object();

    private StorageType storageType = StorageType.SQLITE;
    private String jdbcUrl;
    private String username;
    private String password;
    private HikariDataSource dataSource;
    private boolean initialized;

    public DatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void connect() throws SQLException {
        synchronized (lifecycleLock) {
            closeInternal();

            storageType = StorageType.fromString(plugin.getConfig().getString("db.type", "sqlite"));
            if (storageType == StorageType.SQLITE) {
                File dbFile = new File(plugin.getDataFolder(), "advancedmarriage.db");
                jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
                username = null;
                password = null;

                try (Connection connection = openDirectConnection()) {
                    setupSchema(connection);
                }
                initialized = true;
                return;
            }

            String host = plugin.getConfig().getString("db.host", "127.0.0.1:3306");
            String database = plugin.getConfig().getString("db.name", "advancedmarriage");
            username = plugin.getConfig().getString("db.user", "root");
            password = plugin.getConfig().getString("db.pass", "");
            jdbcUrl = buildRemoteJdbcUrl(storageType, host, database);
            RemoteJdbcProperties remoteJdbcProperties = readRemoteJdbcProperties();

            HikariDataSource newDataSource = createRemoteDataSource(remoteJdbcProperties);
            try (Connection connection = newDataSource.getConnection()) {
                setupSchema(connection);
            } catch (SQLException exception) {
                newDataSource.close();
                throw exception;
            }

            dataSource = newDataSource;
            initialized = true;
        }
    }

    private HikariDataSource createRemoteDataSource(RemoteJdbcProperties remoteJdbcProperties) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setPoolName(plugin.getConfig().getString("db.pool-settings.pool-name", "AdvancedMarriagePool"));
        hikariConfig.setJdbcUrl(jdbcUrl);
        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password);

        // Apply driver properties only for MySQL to keep other drivers untouched and predictable.
        if (storageType == StorageType.MYSQL) {
            applyMysqlDriverProperties(hikariConfig, remoteJdbcProperties);
        }

        // Keep defaults safe but configurable for production tuning.
        hikariConfig.setMaximumPoolSize(plugin.getConfig().getInt("db.pool-settings.maximum-pool-size", 10));
        hikariConfig.setMinimumIdle(plugin.getConfig().getInt("db.pool-settings.minimum-idle", 10));
        hikariConfig.setConnectionTimeout(plugin.getConfig().getLong("db.pool-settings.connection-timeout", 5000L));
        hikariConfig.setValidationTimeout(plugin.getConfig().getLong("db.pool-settings.validation-timeout", 3000L));
        hikariConfig.setIdleTimeout(plugin.getConfig().getLong("db.pool-settings.idle-timeout", 600000L));
        hikariConfig.setMaxLifetime(plugin.getConfig().getLong("db.pool-settings.maximum-lifetime", 1800000L));
        hikariConfig.setKeepaliveTime(plugin.getConfig().getLong("db.pool-settings.keepalive-time", 0L));
        hikariConfig.setLeakDetectionThreshold(plugin.getConfig().getLong("db.pool-settings.leak-detection-threshold", 0L));
        hikariConfig.setInitializationFailTimeout(plugin.getConfig().getLong("db.pool-settings.initialization-fail-timeout", 1L));
        hikariConfig.setAutoCommit(plugin.getConfig().getBoolean("db.pool-settings.auto-commit", true));

        return new HikariDataSource(hikariConfig);
    }

    private void applyMysqlDriverProperties(HikariConfig hikariConfig, RemoteJdbcProperties remoteJdbcProperties) {
        // Keep backward-compatible defaults while allowing operators to tune security/locale behavior.
        hikariConfig.addDataSourceProperty("sslMode", resolveMySqlSslMode(remoteJdbcProperties.useSsl(), remoteJdbcProperties.checkServerCertificate()));
        hikariConfig.addDataSourceProperty("allowPublicKeyRetrieval", remoteJdbcProperties.allowPublicKeyRetrieval());

        if (!remoteJdbcProperties.encoding().isBlank()) {
            hikariConfig.addDataSourceProperty("characterEncoding", remoteJdbcProperties.encoding());
        }
        if (!remoteJdbcProperties.timezone().isBlank()) {
            hikariConfig.addDataSourceProperty("connectionTimeZone", remoteJdbcProperties.timezone());
        }
    }

    String resolveMySqlSslMode(boolean useSsl, boolean checkServerCertificate) {
        if (!useSsl) {
            return "DISABLED";
        }
        return checkServerCertificate ? "VERIFY_CA" : "REQUIRED";
    }

    private RemoteJdbcProperties readRemoteJdbcProperties() {
        boolean checkServerCertificate = plugin.getConfig().getBoolean("db.property.check-server-certificate", false);
        boolean useSsl = plugin.getConfig().getBoolean("db.property.use-ssl", false);
        String encoding = plugin.getConfig().getString("db.property.encoding", "utf8");
        String timezone = plugin.getConfig().getString("db.property.timezone", "Asia/Ho_Chi_Minh");
        boolean allowPublicKeyRetrieval = plugin.getConfig().getBoolean("db.property.allow-public-key-retrieval", true);
        return new RemoteJdbcProperties(
                checkServerCertificate,
                useSsl,
                encoding.trim(),
                timezone.trim(),
                allowPublicKeyRetrieval
        );
    }

    String buildRemoteJdbcUrl(StorageType storageType, String host, String database) {
        return switch (storageType) {
            case MYSQL -> "jdbc:mysql://" + host + "/" + database;
            case MARIADB -> "jdbc:mariadb://" + host + "/" + database;
            case POSTGRES -> "jdbc:postgresql://" + host + "/" + database;
            default -> throw new IllegalStateException("Unexpected value: " + storageType);
        };
    }

    private void setupSchema(Connection connection) throws SQLException {
        StorageType type = storageType;
        String idDefinition = buildIdDefinition(type);

        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS am_marriages ("
                    + idDefinition
                    + "player_a_uuid VARCHAR(36) NOT NULL UNIQUE,"
                    + "player_b_uuid VARCHAR(36) NOT NULL UNIQUE,"
                    + "married_at BIGINT NOT NULL,"
                    + "server_id VARCHAR(100) NOT NULL,"
                    + "pvp_enabled BOOLEAN NOT NULL DEFAULT TRUE"
                    + ")");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS am_homes ("
                    + "marriage_id BIGINT PRIMARY KEY,"
                    + "world_name VARCHAR(255) NOT NULL,"
                    + "server_id VARCHAR(100) NOT NULL,"
                    + "x DOUBLE, y DOUBLE, z DOUBLE,"
                    + "yaw FLOAT, pitch FLOAT"
                    + ")");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS am_chests ("
                    + "marriage_id BIGINT PRIMARY KEY,"
                    + "server_id VARCHAR(100) NOT NULL,"
                    + "size INT NOT NULL,"
                    + "data LONGTEXT,"
                    + "version BIGINT NOT NULL DEFAULT 0,"
                    + "lock_owner VARCHAR(100),"
                    + "lock_token VARCHAR(64),"
                    + "lock_until BIGINT,"
                    + "updated_at BIGINT NOT NULL DEFAULT 0,"
                    + "last_saved_by VARCHAR(100)"
                    + ")");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS am_preferences ("
                    + "player_uuid VARCHAR(36) PRIMARY KEY,"
                    + "display_uuid VARCHAR(36),"
                    + "partner_chat_enabled BOOLEAN NOT NULL DEFAULT FALSE,"
                    + "tp_allow_partner BOOLEAN NOT NULL DEFAULT TRUE"
                    + ")");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS am_revenge ("
                    + idDefinition
                    + "revenger_uuid VARCHAR(36) NOT NULL,"
                    + "killer_uuid VARCHAR(36) NOT NULL,"
                    + "points INT DEFAULT 1,"
                    + "expire_time TIMESTAMP NOT NULL,"
                    + "UNIQUE(revenger_uuid, killer_uuid)"
                    + ")");

            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_am_revenge_revenger ON am_revenge(revenger_uuid)");

            // Run additive migration before creating indexes that depend on new columns.
            migrateChestSchema(statement);
        }
    }

    private void migrateChestSchema(Statement statement) {
        // Additive migration keeps old installs compatible without destructive changes.
        executeAlterIgnoreDuplicate(statement, "ALTER TABLE am_chests ADD COLUMN version BIGINT NOT NULL DEFAULT 0");
        executeAlterIgnoreDuplicate(statement, "ALTER TABLE am_chests ADD COLUMN lock_owner VARCHAR(100)");
        executeAlterIgnoreDuplicate(statement, "ALTER TABLE am_chests ADD COLUMN lock_token VARCHAR(64)");
        executeAlterIgnoreDuplicate(statement, "ALTER TABLE am_chests ADD COLUMN lock_until BIGINT");
        executeAlterIgnoreDuplicate(statement, "ALTER TABLE am_chests ADD COLUMN updated_at BIGINT NOT NULL DEFAULT 0");
        executeAlterIgnoreDuplicate(statement, "ALTER TABLE am_chests ADD COLUMN last_saved_by VARCHAR(100)");

        try {
            statement.executeUpdate("UPDATE am_chests SET version = 0 WHERE version IS NULL");
            statement.executeUpdate("UPDATE am_chests SET updated_at = 0 WHERE updated_at IS NULL");
        } catch (SQLException ignored) {
        }

        try {
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_am_chests_lock_until ON am_chests(lock_until)");
        } catch (SQLException ignored) {
        }
    }

    private void executeAlterIgnoreDuplicate(Statement statement, String sql) {
        try {
            statement.executeUpdate(sql);
        } catch (SQLException ex) {
            String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
            if (message.contains("duplicate") || message.contains("already exists") || message.contains("exists")) {
                return;
            }
            plugin.getLogger().warning("Schema migration warning for SQL [" + sql + "]: " + ex.getMessage());
        }
    }

    private String buildIdDefinition(StorageType type) {
        return switch (type) {
            case SQLITE -> "id INTEGER PRIMARY KEY AUTOINCREMENT,";
            case POSTGRES -> "id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,";
            default -> "id BIGINT AUTO_INCREMENT PRIMARY KEY,";
        };
    }

    public StorageType getStorageType() {
        return storageType;
    }

    public Connection getConnection() throws SQLException {
        if (!initialized) {
            throw new SQLException("Database manager is not initialized.");
        }

        if (storageType == StorageType.SQLITE) {
            return openDirectConnection();
        }

        if (dataSource == null) {
            throw new SQLException("Remote datasource is not available.");
        }

        return dataSource.getConnection();
    }

    public void close() {
        synchronized (lifecycleLock) {
            closeInternal();
        }
    }

    private Connection openDirectConnection() throws SQLException {
        if (storageType == StorageType.SQLITE) {
            return DriverManager.getConnection(jdbcUrl);
        }
        return DriverManager.getConnection(jdbcUrl, username, password);
    }

    private void closeInternal() {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
        initialized = false;
    }

    private record RemoteJdbcProperties(
            boolean checkServerCertificate,
            boolean useSsl,
            String encoding,
            String timezone,
            boolean allowPublicKeyRetrieval
    ) {
    }
}
