package com.lyrinth.advancedmarriage.service;

import com.lyrinth.advancedmarriage.util.WorldFilter;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class ConfigService {
    private final JavaPlugin plugin;

    public ConfigService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public String getServerId() {
        return plugin.getConfig().getString("server-id", "default-server");
    }

    public boolean isFeatureEnabled(String featureKey) {
        return plugin.getConfig().getBoolean("features." + featureKey, true);
    }

    public int getChestSize() {
        int size = plugin.getConfig().getInt("defaults.chest_size", 36);
        if (size < 27 || size > 54 || size % 9 != 0) {
            return 36;
        }
        return size;
    }

    public int getDivorceCooldownMinutes() {
        return plugin.getConfig().getInt("defaults.divorce_cooldown_minutes", 1440);
    }

    public int getMarryRequestTimeoutSeconds() {
        return plugin.getConfig().getInt("defaults.marry_request_timeout_seconds", 300);
    }

    public int getTeleportCountdownSeconds() {
        return plugin.getConfig().getInt("defaults.teleport_countdown_seconds", 3);
    }

    public WorldFilter getTpFilter() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("world_filters.tp");
        return WorldFilter.fromConfig(section);
    }

    public WorldFilter getChestFilter() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("world_filters.chest");
        return WorldFilter.fromConfig(section);
    }

    public WorldFilter getHomeFilter() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("world_filters.home");
        return WorldFilter.fromConfig(section);
    }

    public WorldFilter getRevengeFilter() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("world_filters.revenge");
        return WorldFilter.fromConfig(section);
    }

    public WorldFilter getAllCommandsFilter() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("world_filters.all_commands");
        return WorldFilter.fromConfig(section);
    }

    public boolean isMarryCostEnabled() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("marry_cost");
        if (section != null) {
            Object nestedValue = section.get("enabled");
            return parseEnabledFlag(nestedValue);
        }

        Object rawValue = plugin.getConfig().get("marry_cost");
        return parseEnabledFlag(rawValue);
    }

    public String getMarryCostCurrency() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("marry_cost");
        if (section != null) {
            return section.getString("currency", "Vault");
        }
        return plugin.getConfig().getString("marry_cost_currency", "Vault");
    }

    public double getMarryCostAmount() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("marry_cost");
        if (section != null) {
            return Math.max(0D, section.getDouble("amount", 0D));
        }
        return Math.max(0D, plugin.getConfig().getDouble("marry_cost_amount", 0D));
    }

    public Sound getPartnerOnlineSound() {
        return getSoundByKeyWithLegacy("marry_partner_online_sound", "ENTITY_PLAYER_LEVELUP");
    }

    public Sound getPartnerOfflineSound() {
        return getSoundByKeyWithLegacy("marry_partner_offline_sound", "ENTITY_PLAYER_BURP");
    }

    public Sound getCountdownSound() {
        return getSoundByKey("countdown", "BLOCK_NOTE_BLOCK_CHIME");
    }

    public Sound getTeleportSound() {
        return getSoundByKey("teleport", "ENTITY_ENDERMAN_TELEPORT");
    }

    public Sound getSoundByKey(String key) {
        return getSoundByKey(key, null);
    }

    public Sound getSoundByKey(String key, String fallbackValue) {
        String path = "sounds." + key;
        String rawValue = fallbackValue == null
                ? plugin.getConfig().getString(path)
                : plugin.getConfig().getString(path, fallbackValue);
        return parseSound(rawValue);
    }

    public Sound getSoundByPath(String path, String fallbackValue) {
        String rawValue = fallbackValue == null
                ? plugin.getConfig().getString(path)
                : plugin.getConfig().getString(path, fallbackValue);
        return parseSound(rawValue);
    }

    public int getSyncRefreshIntervalSeconds() {
        return Math.max(0, plugin.getConfig().getInt("sync.refresh_interval_seconds", 30));
    }

    public boolean isCrossServerChestEnabled() {
        return plugin.getConfig().getBoolean("chest.cross_server_enabled", false);
    }

    public int getChestLockTtlSeconds() {
        return Math.max(5, plugin.getConfig().getInt("chest.lock_ttl_seconds", 15));
    }

    public int getChestCheckpointSeconds() {
        return Math.max(1, plugin.getConfig().getInt("chest.checkpoint_seconds", 5));
    }

    public boolean isMarryBroadcastEnabled() {
        return plugin.getConfig().getBoolean("marry-broadcast.enabled", false);
    }

    public List<String> getMarryBroadcastMessages() {
        return plugin.getConfig().getStringList("marry-broadcast.message")
                .stream()
                .filter(line -> line != null && !line.isBlank())
                .toList();
    }

    private Sound getSoundByKeyWithLegacy(String key, String fallbackValue) {
        String nestedPath = "sounds." + key;
        if (plugin.getConfig().contains(nestedPath)) {
            return parseSound(plugin.getConfig().getString(nestedPath, fallbackValue));
        }
        return parseSound(plugin.getConfig().getString(key, fallbackValue));
    }

    private Sound parseSound(String rawName) {
        String normalized = rawName == null ? "" : rawName.trim().toUpperCase().replace('.', '_');
        if (normalized.isEmpty() || "NONE".equals(normalized)) {
            return null;
        }

        try {
            return Sound.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private boolean parseEnabledFlag(Object rawValue) {
        if (rawValue instanceof Boolean boolValue) {
            return boolValue;
        }
        if (rawValue instanceof String stringValue) {
            String normalized = stringValue.trim().toLowerCase();
            return "enabled".equals(normalized) || "true".equals(normalized) || "on".equals(normalized);
        }
        return false;
    }
}
