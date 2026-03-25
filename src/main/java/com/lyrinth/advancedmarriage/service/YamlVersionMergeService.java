package com.lyrinth.advancedmarriage.service;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

public class YamlVersionMergeService {

    public boolean mergeFromPluginResource(JavaPlugin plugin, String fileName) throws IOException {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            throw new IOException("Failed to create plugin data folder: " + dataFolder.getAbsolutePath());
        }

        File targetFile = new File(dataFolder, fileName);
        if (!targetFile.exists()) {
            // Generate the file from bundled defaults on first startup.
            plugin.saveResource(fileName, false);
            return true;
        }

        try (InputStream inputStream = plugin.getResource(fileName)) {
            if (inputStream == null) {
                return false;
            }
            try (Reader defaultReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                return mergeMissingKeys(targetFile, defaultReader);
            }
        }
    }

    public boolean mergeMissingKeys(File targetFile, Reader defaultReader) throws IOException {
        YamlConfiguration userConfig = YamlConfiguration.loadConfiguration(targetFile);
        YamlConfiguration bundledConfig = YamlConfiguration.loadConfiguration(defaultReader);

        // Add only missing paths so user customization remains untouched.
        boolean changed = mergeMissingSection(bundledConfig, userConfig);

        // Keep user version updated when plugin ships a newer schema version.
        int bundledVersion = readVersion(bundledConfig);
        int userVersion = readVersion(userConfig);
        if (bundledVersion >= 0 && userVersion < bundledVersion) {
            userConfig.set("version", bundledVersion);
            changed = true;
        }

        if (changed) {
            userConfig.save(targetFile);
        }
        return changed;
    }

    private boolean mergeMissingSection(ConfigurationSection defaults, ConfigurationSection target) {
        boolean changed = false;

        for (String key : defaults.getKeys(false)) {
            Object defaultValue = defaults.get(key);
            if (!target.contains(key)) {
                target.set(key, defaultValue);
                changed = true;
                continue;
            }

            ConfigurationSection defaultChild = defaults.getConfigurationSection(key);
            ConfigurationSection targetChild = target.getConfigurationSection(key);
            if (defaultChild != null && targetChild != null) {
                changed |= mergeMissingSection(defaultChild, targetChild);
            }
        }

        return changed;
    }

    private int readVersion(ConfigurationSection section) {
        Object rawVersion = section.get("version");
        if (rawVersion instanceof Number number) {
            return number.intValue();
        }
        if (rawVersion instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        return -1;
    }
}
