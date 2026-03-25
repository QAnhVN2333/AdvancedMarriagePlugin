package com.lyrinth.advancedmarriage.util;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

public class WorldFilter {
    private final String mode;
    private final List<String> whitelist;
    private final List<String> blacklist;

    public WorldFilter(String mode, List<String> whitelist, List<String> blacklist) {
        this.mode = mode == null ? "whitelist" : mode.toLowerCase();
        this.whitelist = whitelist;
        this.blacklist = blacklist;
    }

    public static WorldFilter fromConfig(ConfigurationSection section) {
        if (section == null) {
            return new WorldFilter("whitelist", List.of(), List.of());
        }
        return new WorldFilter(
                section.getString("type", "whitelist"),
                section.getStringList("whitelist"),
                section.getStringList("blacklist")
        );
    }

    public boolean isAllowed(String worldName) {
        String normalized = worldName.toLowerCase();
        if ("blacklist".equals(mode)) {
            return blacklist.stream().noneMatch(w -> w.equalsIgnoreCase(normalized));
        }
        if (whitelist.isEmpty()) {
            return true;
        }
        return whitelist.stream().anyMatch(w -> w.equalsIgnoreCase(normalized));
    }
}

