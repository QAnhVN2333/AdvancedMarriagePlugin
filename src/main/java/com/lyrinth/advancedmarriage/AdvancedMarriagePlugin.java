package com.lyrinth.advancedmarriage;

import com.lyrinth.advancedmarriage.command.MarriageCommand;
import com.lyrinth.advancedmarriage.database.DatabaseManager;
import com.lyrinth.advancedmarriage.gui.CoupleListGui;
import com.lyrinth.advancedmarriage.listener.ChatListener;
import com.lyrinth.advancedmarriage.listener.InventoryListener;
import com.lyrinth.advancedmarriage.listener.PartnerStatusListener;
import com.lyrinth.advancedmarriage.listener.PvpListener;
import com.lyrinth.advancedmarriage.listener.RevengeListener;
import com.lyrinth.advancedmarriage.listener.TeleportCancelListener;
import com.lyrinth.advancedmarriage.service.ChestService;
import com.lyrinth.advancedmarriage.service.ChestSessionService;
import com.lyrinth.advancedmarriage.service.ConfigService;
import com.lyrinth.advancedmarriage.service.HomeService;
import com.lyrinth.advancedmarriage.service.MarriageService;
import com.lyrinth.advancedmarriage.service.MarryCostService;
import com.lyrinth.advancedmarriage.service.MessageService;
import com.lyrinth.advancedmarriage.service.PreferenceService;
import com.lyrinth.advancedmarriage.service.RevengeService;
import com.lyrinth.advancedmarriage.service.SoundService;
import com.lyrinth.advancedmarriage.service.TeleportService;
import com.lyrinth.advancedmarriage.service.VaultCostProvider;
import com.lyrinth.advancedmarriage.service.YamlVersionMergeService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.Map;

public class AdvancedMarriagePlugin extends JavaPlugin {
    private DatabaseManager databaseManager;
    private ChestSessionService chestSessionService;
    private RevengeService revengeService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        YamlVersionMergeService yamlVersionMergeService = new YamlVersionMergeService();
        try {
            // Merge only missing keys from bundled files to preserve user custom values.
            yamlVersionMergeService.mergeFromPluginResource(this, "config.yml");
            yamlVersionMergeService.mergeFromPluginResource(this, "messages.yml");
        } catch (IOException ex) {
            getLogger().warning("Failed to merge YAML defaults: " + ex.getMessage());
        }
        reloadConfig();

        this.databaseManager = new DatabaseManager(this);
        ConfigService configService = new ConfigService(this);
        autoDisableMarryCostWhenVaultMissing(configService);
        MessageService messageService = new MessageService(this);
        SoundService soundService = new SoundService(configService);

        try {
            databaseManager.connect();
            getLogger().info("Database initialized with storage type: " + databaseManager.getStorageType().name());

            MarriageService marriageService = new MarriageService(databaseManager);
            marriageService.loadMarriages();
            HomeService homeService = new HomeService(databaseManager);
            ChestService chestService = new ChestService(databaseManager);
            this.chestSessionService = new ChestSessionService(chestService, configService);
            PreferenceService preferenceService = new PreferenceService(databaseManager);
            TeleportService teleportService = new TeleportService(this, configService, messageService);
            MarryCostService marryCostService = new MarryCostService(this, configService);
            CoupleListGui coupleListGui = new CoupleListGui(marriageService, preferenceService, messageService);
            this.revengeService = new RevengeService(this, databaseManager, configService, messageService);
            this.revengeService.loadFromDatabase();

            MarriageCommand commandExecutor = new MarriageCommand(
                    marriageService,
                    homeService,
                    chestService,
                    chestSessionService,
                    preferenceService,
                    messageService,
                    configService,
                    teleportService,
                    marryCostService,
                    coupleListGui,
                    soundService,
                    yamlVersionMergeService,
                    this
            );

            PluginCommand command = getCommand("marry");
            if (command != null) {
                command.setExecutor(commandExecutor);
                command.setTabCompleter(commandExecutor);
            }

            getServer().getPluginManager().registerEvents(
                    new InventoryListener(coupleListGui, chestSessionService, messageService, soundService), this);
            getServer().getPluginManager().registerEvents(
                    new ChatListener(marriageService, preferenceService, messageService, soundService), this);
            getServer().getPluginManager().registerEvents(
                    new PvpListener(marriageService, configService), this);
            getServer().getPluginManager().registerEvents(
                    new TeleportCancelListener(teleportService), this);
            getServer().getPluginManager().registerEvents(
                    new PartnerStatusListener(marriageService, messageService, configService), this);
            getServer().getPluginManager().registerEvents(
                    new RevengeListener(marriageService, revengeService, messageService), this);

            startChestSessionSyncTask();
            startMarriageRefreshTask(marriageService, configService);
            startRevengeRadarTask();
            startRevengeAutoSaveTask();

            getLogger().info("AdvancedMarriage enabled successfully.");
        } catch (Exception ex) {
            getLogger().severe("Failed to enable AdvancedMarriage: " + ex.getMessage());
            getLogger().severe("Startup exception type: " + ex.getClass().getSimpleName());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void startChestSessionSyncTask() {
        if (chestSessionService == null) {
            return;
        }

        getServer().getScheduler().runTaskTimer(this, () -> {
            try {
                Map<Long, Exception> failures = chestSessionService.tickSessions();
                for (Map.Entry<Long, Exception> entry : failures.entrySet()) {
                    getLogger().warning("Shared chest sync failed for marriage " + entry.getKey() + ": " + entry.getValue().getMessage());
                }
            } catch (Exception ex) {
                getLogger().warning("Unexpected shared chest sync failure: " + ex.getMessage());
            }
        }, 20L, 20L);
    }

    private void startMarriageRefreshTask(MarriageService marriageService, ConfigService configService) {
        int intervalSeconds = configService.getSyncRefreshIntervalSeconds();
        if (intervalSeconds <= 0) {
            return;
        }

        long intervalTicks = intervalSeconds * 20L;
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            try {
                marriageService.loadMarriages();
            } catch (Exception ex) {
                getLogger().warning("Failed to refresh marriage cache: " + ex.getMessage());
            }
        }, intervalTicks, intervalTicks);
    }

    private void startRevengeRadarTask() {
        if (revengeService == null) {
            return;
        }

        getServer().getScheduler().runTaskTimer(this, () -> {
            try {
                revengeService.scanRadar();
            } catch (Exception ex) {
                getLogger().warning("Failed to scan revenge radar: " + ex.getMessage());
            }
        }, 40L, 40L);
    }

    private void startRevengeAutoSaveTask() {
        if (revengeService == null) {
            return;
        }

        long intervalTicks = 15L * 60L * 20L;
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            try {
                revengeService.saveDirtyToDatabase();
            } catch (Exception ex) {
                getLogger().warning("Failed to save revenge cache: " + ex.getMessage());
            }
        }, intervalTicks, intervalTicks);
    }

    private void autoDisableMarryCostWhenVaultMissing(ConfigService configService) {
        if (!configService.isMarryCostEnabled()) {
            return;
        }

        String currency = configService.getMarryCostCurrency();
        if (currency == null || !"vault".equalsIgnoreCase(currency.trim())) {
            return;
        }

        if (VaultCostProvider.isVaultInstalled(this)) {
            return;
        }

        getLogger().warning("Vault was not found, so marry_cost.enabled has been set to false automatically.");
        getConfig().set("marry_cost.enabled", false);
        saveConfig();
        reloadConfig();
    }

    @Override
    public void onDisable() {
        if (chestSessionService != null) {
            Map<Long, Exception> flushFailures = chestSessionService.flushAllSessions();
            for (Map.Entry<Long, Exception> entry : flushFailures.entrySet()) {
                getLogger().warning("Failed to flush shared chest for marriage " + entry.getKey() + ": " + entry.getValue().getMessage());
            }
        }

        if (revengeService != null) {
            revengeService.flushOnShutdown();
        }

        if (databaseManager != null) {
            databaseManager.close();
        }
    }
}
