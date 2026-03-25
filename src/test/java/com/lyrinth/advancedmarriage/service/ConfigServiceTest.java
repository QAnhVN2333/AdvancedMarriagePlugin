package com.lyrinth.advancedmarriage.service;

import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfigServiceTest {

    @Test
    void shouldReadNestedMarryCostStructure() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        FileConfiguration config = new YamlConfiguration();
        config.set("marry_cost.enabled", true);
        config.set("marry_cost.currency", "PlayerPoints");
        config.set("marry_cost.amount", 2500);
        when(plugin.getConfig()).thenReturn(config);

        ConfigService service = new ConfigService(plugin);

        assertTrue(service.isMarryCostEnabled());
        assertEquals("PlayerPoints", service.getMarryCostCurrency());
        assertEquals(2500D, service.getMarryCostAmount());
    }

    @Test
    void shouldSupportLegacyMarryCostKeysForBackwardCompatibility() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        FileConfiguration config = new YamlConfiguration();
        config.set("marry_cost", "enabled");
        config.set("marry_cost_currency", "Vault");
        config.set("marry_cost_amount", 1000);
        when(plugin.getConfig()).thenReturn(config);

        ConfigService service = new ConfigService(plugin);

        assertTrue(service.isMarryCostEnabled());
        assertEquals("Vault", service.getMarryCostCurrency());
        assertEquals(1000D, service.getMarryCostAmount());
    }

    @Test
    void shouldDisableMarryCostWhenNestedFlagIsFalse() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        FileConfiguration config = new YamlConfiguration();
        config.set("marry_cost.enabled", false);
        when(plugin.getConfig()).thenReturn(config);

        ConfigService service = new ConfigService(plugin);

        assertFalse(service.isMarryCostEnabled());
    }

    @Test
    void shouldReadPartnerSoundsFromNestedSoundsSection() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        FileConfiguration config = new YamlConfiguration();
        config.set("sounds.marry_partner_online_sound", "ENTITY_ARROW_HIT_PLAYER");
        config.set("sounds.marry_partner_offline_sound", "ENTITY_VILLAGER_NO");
        when(plugin.getConfig()).thenReturn(config);

        ConfigService service = new ConfigService(plugin);

        assertEquals(Sound.ENTITY_ARROW_HIT_PLAYER, service.getPartnerOnlineSound());
        assertEquals(Sound.ENTITY_VILLAGER_NO, service.getPartnerOfflineSound());
    }

    @Test
    void shouldFallbackToLegacyPartnerSoundKeysWhenNestedKeysAreMissing() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        FileConfiguration config = new YamlConfiguration();
        config.set("marry_partner_online_sound", "ENTITY_ARROW_HIT_PLAYER");
        config.set("marry_partner_offline_sound", "ENTITY_VILLAGER_NO");
        when(plugin.getConfig()).thenReturn(config);

        ConfigService service = new ConfigService(plugin);

        assertEquals(Sound.ENTITY_ARROW_HIT_PLAYER, service.getPartnerOnlineSound());
        assertEquals(Sound.ENTITY_VILLAGER_NO, service.getPartnerOfflineSound());
    }

    @Test
    void shouldReadRevengeWorldFilterInWhitelistMode() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        FileConfiguration config = new YamlConfiguration();
        config.set("world_filters.revenge.type", "whitelist");
        config.set("world_filters.revenge.whitelist", java.util.List.of("world", "arena"));
        when(plugin.getConfig()).thenReturn(config);

        ConfigService service = new ConfigService(plugin);

        assertTrue(service.getRevengeFilter().isAllowed("world"));
        assertTrue(service.getRevengeFilter().isAllowed("ARENA"));
        assertFalse(service.getRevengeFilter().isAllowed("nether"));
    }

    @Test
    void shouldReadRevengeWorldFilterInBlacklistMode() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        FileConfiguration config = new YamlConfiguration();
        config.set("world_filters.revenge.type", "blacklist");
        config.set("world_filters.revenge.blacklist", java.util.List.of("pvp_world"));
        when(plugin.getConfig()).thenReturn(config);

        ConfigService service = new ConfigService(plugin);

        assertFalse(service.getRevengeFilter().isAllowed("pvp_world"));
        assertTrue(service.getRevengeFilter().isAllowed("survival"));
    }

    @Test
    void shouldReadMarryBroadcastConfig() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        FileConfiguration config = new YamlConfiguration();
        config.set("marry-broadcast.enabled", true);
        config.set("marry-broadcast.message", List.of("&a{player1} + {player2}", "&bCongrats"));
        when(plugin.getConfig()).thenReturn(config);

        ConfigService service = new ConfigService(plugin);

        assertTrue(service.isMarryBroadcastEnabled());
        assertEquals(List.of("&a{player1} + {player2}", "&bCongrats"), service.getMarryBroadcastMessages());
    }

    @Test
    void shouldIgnoreBlankMarryBroadcastMessages() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        FileConfiguration config = new YamlConfiguration();
        config.set("marry-broadcast.message", List.of(" ", "&aValid", ""));
        when(plugin.getConfig()).thenReturn(config);

        ConfigService service = new ConfigService(plugin);

        assertEquals(List.of("&aValid"), service.getMarryBroadcastMessages());
    }
}
