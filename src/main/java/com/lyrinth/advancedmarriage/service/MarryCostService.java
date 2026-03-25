package com.lyrinth.advancedmarriage.service;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.UUID;

public class MarryCostService {
    private final ConfigService configService;
    private final MarriageCostProvider provider;

    public MarryCostService(JavaPlugin plugin, ConfigService configService) {
        this.configService = configService;
        this.provider = createProvider(plugin, configService.getMarryCostCurrency());
    }

    public boolean isEnabled() {
        return configService.isMarryCostEnabled() && configService.getMarryCostAmount() > 0D;
    }

    public boolean isAvailable() {
        return !isEnabled() || provider.isAvailable();
    }

    public boolean canAfford(UUID playerUuid) {
        if (!isEnabled()) {
            return true;
        }
        if (!provider.isAvailable()) {
            return false;
        }
        return provider.has(playerUuid, configService.getMarryCostAmount());
    }

    public boolean charge(UUID playerUuid) {
        if (!isEnabled()) {
            return true;
        }
        if (!provider.isAvailable()) {
            return false;
        }
        return provider.withdraw(playerUuid, configService.getMarryCostAmount());
    }

    public String getCurrencyName() {
        return provider.getCurrencyName();
    }

    public String getFormattedAmount() {
        double amount = configService.getMarryCostAmount();
        if (Math.floor(amount) == amount) {
            return String.valueOf((long) amount);
        }
        return String.format(Locale.US, "%.2f", amount);
    }

    private MarriageCostProvider createProvider(JavaPlugin plugin, String currencyName) {
        String normalized = currencyName == null ? "vault" : currencyName.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "playerpoint", "playerpoints" -> new PlayerPointsCostProvider(plugin);
            case "vault" -> new VaultCostProvider(plugin);
            default -> new VaultCostProvider(plugin);
        };
    }
}

