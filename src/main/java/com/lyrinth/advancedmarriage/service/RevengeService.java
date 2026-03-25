package com.lyrinth.advancedmarriage.service;

import com.lyrinth.advancedmarriage.database.DatabaseManager;
import com.lyrinth.advancedmarriage.database.StorageType;
import com.lyrinth.advancedmarriage.model.RevengeKey;
import com.lyrinth.advancedmarriage.model.RevengeRecord;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RevengeService {
    private static final String BASE_SELECT_SQL = "SELECT revenger_uuid, killer_uuid, points, expire_time FROM am_revenge";

    private final JavaPlugin plugin;
    private final DatabaseManager databaseManager;
    private final ConfigService configService;
    private final MessageService messageService;

    private final Map<RevengeKey, RevengeRecord> cache = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> killersByRevenger = new ConcurrentHashMap<>();
    private final Set<UUID> onlineRevengers = ConcurrentHashMap.newKeySet();
    private final Set<RevengeKey> dirtyUpserts = ConcurrentHashMap.newKeySet();
    private final Set<RevengeKey> dirtyDeletes = ConcurrentHashMap.newKeySet();
    private final Map<RevengeKey, Long> warningCooldownByKey = new ConcurrentHashMap<>();
    private final Object dirtyLock = new Object();

    public RevengeService(JavaPlugin plugin, DatabaseManager databaseManager, ConfigService configService, MessageService messageService) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.configService = configService;
        this.messageService = messageService;
    }

    public void loadFromDatabase() throws SQLException {
        cache.clear();
        killersByRevenger.clear();
        onlineRevengers.clear();
        warningCooldownByKey.clear();

        Instant now = Instant.now();
        try (Connection connection = databaseManager.getConnection()) {
            deleteExpiredRows(connection, now);

            try (PreparedStatement statement = connection.prepareStatement(BASE_SELECT_SQL + " WHERE expire_time > ?")) {
                statement.setTimestamp(1, Timestamp.from(now));
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        UUID revengerUuid = UUID.fromString(resultSet.getString("revenger_uuid"));
                        UUID killerUuid = UUID.fromString(resultSet.getString("killer_uuid"));
                        int points = Math.max(1, resultSet.getInt("points"));
                        Timestamp expireTimestamp = resultSet.getTimestamp("expire_time");
                        if (expireTimestamp == null) {
                            continue;
                        }

                        Instant expireTime = expireTimestamp.toInstant();
                        if (!expireTime.isAfter(now)) {
                            continue;
                        }

                        RevengeKey key = new RevengeKey(revengerUuid, killerUuid);
                        cache.put(key, new RevengeRecord(points, expireTime));
                        killersByRevenger.computeIfAbsent(revengerUuid, ignored -> ConcurrentHashMap.newKeySet()).add(killerUuid);
                    }
                }
            }
        }

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (killersByRevenger.containsKey(onlinePlayer.getUniqueId())) {
                onlineRevengers.add(onlinePlayer.getUniqueId());
            }
        }
    }

    public int grantRevenge(UUID revengerUuid, UUID killerUuid) {
        if (!isEnabled()) {
            return 0;
        }

        Instant newExpireTime = Instant.now().plus(getExpireHours(), ChronoUnit.HOURS);
        RevengeKey key = new RevengeKey(revengerUuid, killerUuid);
        RevengeRecord updated = cache.compute(key, (ignored, existing) -> {
            int points = existing == null ? 1 : existing.points() + 1;
            return new RevengeRecord(points, newExpireTime);
        });

        killersByRevenger.computeIfAbsent(revengerUuid, ignored -> ConcurrentHashMap.newKeySet()).add(killerUuid);
        if (Bukkit.getPlayer(revengerUuid) != null) {
            onlineRevengers.add(revengerUuid);
        }

        markDirtyUpsert(key);
        return updated.points();
    }

    public boolean hasActiveRevenge(UUID revengerUuid, UUID killerUuid) {
        RevengeKey key = new RevengeKey(revengerUuid, killerUuid);
        RevengeRecord record = cache.get(key);
        if (record == null) {
            return false;
        }

        if (isExpired(record)) {
            removeKey(key);
            return false;
        }

        return record.points() > 0;
    }

    public int completeRevenge(UUID revengerUuid, UUID killerUuid) {
        RevengeKey key = new RevengeKey(revengerUuid, killerUuid);
        RevengeRecord removed = cache.remove(key);
        if (removed == null) {
            return 0;
        }

        removeFromIndexes(key);
        markDirtyDelete(key);
        return removed.points();
    }

    public void applyBuffs(Player revenger, Player killer) {
        if (!isEnabled() || !isBuffEnabled() || revenger == null || killer == null) {
            return;
        }

        if (!isWorldAllowed(revenger.getWorld().getName()) || !isWorldAllowed(killer.getWorld().getName())) {
            return;
        }

        RevengeKey key = new RevengeKey(revenger.getUniqueId(), killer.getUniqueId());
        RevengeRecord record = cache.get(key);
        if (record == null) {
            return;
        }

        if (isExpired(record)) {
            removeKey(key);
            return;
        }

        ConfiguredBuffTier configuredTier = resolveBuffTierForPoints(record.points());
        for (ConfiguredEffect configuredEffect : configuredTier.effects()) {
            // Force reapply keeps the buff refreshed after every successful hit.
            revenger.addPotionEffect(new PotionEffect(
                    configuredEffect.type(),
                    configuredEffect.durationTicks(),
                    configuredEffect.amplifier(),
                    false,
                    true,
                    true
            ), true);
        }

        runConsoleCommands(configuredTier.consoleCommands(), revenger, killer, record.points());
        runPlayerCommands(configuredTier.playerCommands(), revenger, killer, record.points());
    }

    public void scanRadar() {
        if (!isEnabled()) {
            return;
        }

        int triggerDistance = getTriggerDistance();
        if (triggerDistance <= 0) {
            return;
        }

        long nowMillis = System.currentTimeMillis();
        long cooldownMillis = Math.max(1, getWarningCooldownSeconds()) * 1000L;
        double triggerDistanceSquared = triggerDistance * (double) triggerDistance;

        for (UUID revengerUuid : new HashSet<>(onlineRevengers)) {
            Player revenger = Bukkit.getPlayer(revengerUuid);
            if (revenger == null) {
                onlineRevengers.remove(revengerUuid);
                continue;
            }

            if (!isWorldAllowed(revenger.getWorld().getName())) {
                continue;
            }

            Set<UUID> killerSet = killersByRevenger.get(revengerUuid);
            if (killerSet == null || killerSet.isEmpty()) {
                onlineRevengers.remove(revengerUuid);
                continue;
            }

            for (UUID killerUuid : new HashSet<>(killerSet)) {
                RevengeKey key = new RevengeKey(revengerUuid, killerUuid);
                if (!hasActiveRevenge(revengerUuid, killerUuid)) {
                    continue;
                }

                Player killer = Bukkit.getPlayer(killerUuid);
                if (killer == null) {
                    continue;
                }

                if (!isWorldAllowed(killer.getWorld().getName())) {
                    continue;
                }

                if (!killer.getWorld().equals(revenger.getWorld())) {
                    continue;
                }

                if (revenger.getLocation().distanceSquared(killer.getLocation()) > triggerDistanceSquared) {
                    continue;
                }

                long lastWarning = warningCooldownByKey.getOrDefault(key, 0L);
                if (nowMillis - lastWarning < cooldownMillis) {
                    continue;
                }

                warningCooldownByKey.put(key, nowMillis);
                messageService.send(revenger, "revenge.radar_warning", Map.of("killer", killer.getName()));

                Sound triggerSound = configService.getSoundByPath("revenge.trigger.sound", "ENTITY_WITHER_SPAWN");
                if (triggerSound != null) {
                    revenger.playSound(revenger.getLocation(), triggerSound, 1f, 1f);
                }
            }
        }
    }

    public void trackPlayerJoin(UUID playerUuid) {
        if (killersByRevenger.containsKey(playerUuid)) {
            onlineRevengers.add(playerUuid);
        }
    }

    public void trackPlayerQuit(UUID playerUuid) {
        onlineRevengers.remove(playerUuid);
    }

    public List<String> getRewardCommands() {
        return plugin.getConfig().getStringList("revenge.reward.commands");
    }

    public List<String> getRewardMessages() {
        return plugin.getConfig().getStringList("revenge.reward.messages");
    }

    public void saveDirtyToDatabase() {
        cleanupExpiredEntries();

        List<RevengeKey> upsertKeys;
        List<RevengeKey> deleteKeys;
        synchronized (dirtyLock) {
            upsertKeys = new ArrayList<>(dirtyUpserts);
            deleteKeys = new ArrayList<>(dirtyDeletes);
            dirtyUpserts.clear();
            dirtyDeletes.clear();
        }

        if (upsertKeys.isEmpty() && deleteKeys.isEmpty()) {
            return;
        }

        try (Connection connection = databaseManager.getConnection()) {
            deleteExpiredRows(connection, Instant.now());
            upsertKeys(connection, upsertKeys);
            deleteKeys(connection, deleteKeys);
        } catch (SQLException ex) {
            requeueDirty(upsertKeys, deleteKeys);
            plugin.getLogger().warning("Failed to save revenge cache: " + ex.getMessage());
        }
    }

    public void flushOnShutdown() {
        saveDirtyToDatabase();
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("revenge.enabled", true);
    }

    private int getExpireHours() {
        return Math.max(1, plugin.getConfig().getInt("revenge.expire_hours", 24));
    }

    private int getTriggerDistance() {
        return Math.max(0, plugin.getConfig().getInt("revenge.trigger.distance", 20));
    }

    private int getWarningCooldownSeconds() {
        return Math.max(1, plugin.getConfig().getInt("revenge.trigger.warning_cooldown_seconds", 60));
    }

    private boolean isBuffEnabled() {
        return plugin.getConfig().getBoolean("revenge.buffs.enabled", true);
    }

    private boolean isBuffStackable() {
        return plugin.getConfig().getBoolean("revenge.buffs.stackable", false);
    }

    public boolean isWorldAllowed(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return false;
        }
        return configService.getRevengeFilter().isAllowed(worldName);
    }

    private ConfiguredBuffTier resolveBuffTierForPoints(int points) {
        ConfigurationSection tiersSection = plugin.getConfig().getConfigurationSection("revenge.buffs.tiers");
        if (tiersSection == null) {
            return ConfiguredBuffTier.empty();
        }

        List<Integer> tiers = new ArrayList<>();
        for (String key : tiersSection.getKeys(false)) {
            try {
                tiers.add(Integer.parseInt(key));
            } catch (NumberFormatException ignored) {
            }
        }

        tiers.sort(Comparator.naturalOrder());
        if (tiers.isEmpty()) {
            return ConfiguredBuffTier.empty();
        }

        List<ConfiguredEffect> resultEffects = new ArrayList<>();
        List<String> resultConsoleCommands = new ArrayList<>();
        List<String> resultPlayerCommands = new ArrayList<>();
        for (int tier : tiers) {
            if (tier > points) {
                break;
            }

            String tierPath = "revenge.buffs.tiers." + tier;
            List<ConfiguredEffect> tierEffects = parseEffects(plugin.getConfig().getMapList(tierPath + ".effects"));
            List<String> tierConsoleCommands = parseCommands(plugin.getConfig().getStringList(tierPath + ".console-commands"));
            List<String> tierPlayerCommands = parseCommands(plugin.getConfig().getStringList(tierPath + ".player-commands"));

            resultEffects.addAll(tierEffects);
            resultConsoleCommands.addAll(tierConsoleCommands);
            resultPlayerCommands.addAll(tierPlayerCommands);

            if (!isBuffStackable()) {
                resultEffects.clear();
                resultEffects.addAll(tierEffects);
                resultConsoleCommands.clear();
                resultConsoleCommands.addAll(tierConsoleCommands);
                resultPlayerCommands.clear();
                resultPlayerCommands.addAll(tierPlayerCommands);
            }
        }

        return new ConfiguredBuffTier(
                List.copyOf(resultEffects),
                List.copyOf(resultConsoleCommands),
                List.copyOf(resultPlayerCommands)
        );
    }

    private List<ConfiguredEffect> parseEffects(List<Map<?, ?>> rawEffects) {
        if (rawEffects == null || rawEffects.isEmpty()) {
            return List.of();
        }

        List<ConfiguredEffect> effects = new ArrayList<>();
        for (Map<?, ?> rawEffect : rawEffects) {
            Object typeRaw = rawEffect.get("type");
            if (!(typeRaw instanceof String typeName)) {
                continue;
            }

            PotionEffectType effectType = PotionEffectType.getByName(typeName.trim().toUpperCase());
            if (effectType == null) {
                continue;
            }

            int level = parseInt(rawEffect.get("level"), 1);
            int duration = parseInt(rawEffect.get("duration"), 200);
            effects.add(new ConfiguredEffect(effectType, Math.max(0, level - 1), Math.max(1, duration)));
        }
        return effects;
    }

    private int parseInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String raw) {
            try {
                return Integer.parseInt(raw);
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private List<String> parseCommands(List<String> rawCommands) {
        if (rawCommands == null || rawCommands.isEmpty()) {
            return List.of();
        }

        List<String> commands = new ArrayList<>();
        for (String command : rawCommands) {
            String normalized = normalizeCommandInput(command);
            if (!normalized.isEmpty()) {
                commands.add(normalized);
            }
        }
        return commands;
    }

    private void runConsoleCommands(List<String> commands, Player revenger, Player opponent, int points) {
        for (String command : commands) {
            String resolved = resolveCommandPlaceholders(command, revenger.getName(), opponent.getName(), points);
            if (resolved.isEmpty()) {
                continue;
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
        }
    }

    private void runPlayerCommands(List<String> commands, Player revenger, Player opponent, int points) {
        if (!revenger.isOnline()) {
            return;
        }

        for (String command : commands) {
            String resolved = resolveCommandPlaceholders(command, revenger.getName(), opponent.getName(), points);
            if (resolved.isEmpty()) {
                continue;
            }
            revenger.performCommand(resolved);
        }
    }

    static String normalizeCommandInput(String command) {
        if (command == null) {
            return "";
        }
        String normalized = command.trim();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1).trim();
        }
        return normalized;
    }

    static String resolveCommandPlaceholders(String input, String playerName, String opponentName, int points) {
        if (input == null) {
            return "";
        }
        String safePlayerName = playerName == null ? "" : playerName;
        String safeOpponentName = opponentName == null ? "" : opponentName;
        return input
                .replace("%player%", safePlayerName)
                .replace("%opponent%", safeOpponentName)
                // Keep %killer% for backward compatibility with old configs.
                .replace("%killer%", safeOpponentName)
                .replace("%points%", String.valueOf(points));
    }

    private void cleanupExpiredEntries() {
        for (Map.Entry<RevengeKey, RevengeRecord> entry : new ArrayList<>(cache.entrySet())) {
            if (isExpired(entry.getValue())) {
                removeKey(entry.getKey());
            }
        }
    }

    private boolean isExpired(RevengeRecord record) {
        return record.expireTime() == null || !record.expireTime().isAfter(Instant.now());
    }

    private void removeKey(RevengeKey key) {
        cache.remove(key);
        removeFromIndexes(key);
        markDirtyDelete(key);
    }

    private void removeFromIndexes(RevengeKey key) {
        warningCooldownByKey.remove(key);
        Set<UUID> killerSet = killersByRevenger.get(key.revengerUuid());
        if (killerSet != null) {
            killerSet.remove(key.killerUuid());
            if (killerSet.isEmpty()) {
                killersByRevenger.remove(key.revengerUuid());
                onlineRevengers.remove(key.revengerUuid());
            }
        }
    }

    private void markDirtyUpsert(RevengeKey key) {
        synchronized (dirtyLock) {
            dirtyDeletes.remove(key);
            dirtyUpserts.add(key);
        }
    }

    private void markDirtyDelete(RevengeKey key) {
        synchronized (dirtyLock) {
            dirtyUpserts.remove(key);
            dirtyDeletes.add(key);
        }
    }

    private void requeueDirty(List<RevengeKey> upsertKeys, List<RevengeKey> deleteKeys) {
        synchronized (dirtyLock) {
            dirtyUpserts.addAll(upsertKeys);
            dirtyDeletes.addAll(deleteKeys);
        }
    }

    private void upsertKeys(Connection connection, List<RevengeKey> upsertKeys) throws SQLException {
        String upsertSql = buildUpsertSql(databaseManager.getStorageType());
        try (PreparedStatement statement = connection.prepareStatement(upsertSql)) {
            for (RevengeKey key : upsertKeys) {
                RevengeRecord record = cache.get(key);
                if (record == null || isExpired(record)) {
                    continue;
                }

                statement.setString(1, key.revengerUuid().toString());
                statement.setString(2, key.killerUuid().toString());
                statement.setInt(3, Math.max(1, record.points()));
                statement.setTimestamp(4, Timestamp.from(record.expireTime()));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void deleteKeys(Connection connection, List<RevengeKey> deleteKeys) throws SQLException {
        if (deleteKeys.isEmpty()) {
            return;
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM am_revenge WHERE revenger_uuid = ? AND killer_uuid = ?")) {
            for (RevengeKey key : deleteKeys) {
                statement.setString(1, key.revengerUuid().toString());
                statement.setString(2, key.killerUuid().toString());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private String buildUpsertSql(StorageType storageType) {
        return switch (storageType) {
            case SQLITE -> "INSERT INTO am_revenge(revenger_uuid, killer_uuid, points, expire_time) VALUES(?, ?, ?, ?) "
                    + "ON CONFLICT(revenger_uuid, killer_uuid) DO UPDATE SET points = excluded.points, expire_time = excluded.expire_time";
            case POSTGRES -> "INSERT INTO am_revenge(revenger_uuid, killer_uuid, points, expire_time) VALUES(?, ?, ?, ?) "
                    + "ON CONFLICT(revenger_uuid, killer_uuid) DO UPDATE SET points = EXCLUDED.points, expire_time = EXCLUDED.expire_time";
            default -> "INSERT INTO am_revenge(revenger_uuid, killer_uuid, points, expire_time) VALUES(?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE points = VALUES(points), expire_time = VALUES(expire_time)";
        };
    }

    private void deleteExpiredRows(Connection connection, Instant now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM am_revenge WHERE expire_time <= ?")) {
            statement.setTimestamp(1, Timestamp.from(now));
            statement.executeUpdate();
        }
    }

    private record ConfiguredEffect(PotionEffectType type, int amplifier, int durationTicks) {
    }

    private record ConfiguredBuffTier(
            List<ConfiguredEffect> effects,
            List<String> consoleCommands,
            List<String> playerCommands
    ) {
        private static ConfiguredBuffTier empty() {
            return new ConfiguredBuffTier(List.of(), List.of(), List.of());
        }
    }
}
