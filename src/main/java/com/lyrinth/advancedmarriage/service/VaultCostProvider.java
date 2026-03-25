package com.lyrinth.advancedmarriage.service;

import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.UUID;

public class VaultCostProvider implements MarriageCostProvider {
    private static final String VAULT_PLUGIN_NAME = "Vault";
    private static final String ECONOMY_CLASS_NAME = "net.milkbowl.vault.economy.Economy";

    private final JavaPlugin plugin;
    private final Plugin vaultPlugin;
    private final Object economy;

    public VaultCostProvider(JavaPlugin plugin) {
        this.plugin = plugin;
        this.vaultPlugin = plugin.getServer().getPluginManager().getPlugin(VAULT_PLUGIN_NAME);
        this.economy = resolveEconomy(plugin);
    }

    @Override
    public String getCurrencyName() {
        return "Vault";
    }

    @Override
    public boolean isAvailable() {
        return isVaultInstalled(plugin) && economy != null;
    }

    @Override
    public boolean has(UUID playerUuid, double amount) {
        if (!isAvailable() || amount <= 0) {
            return amount <= 0;
        }

        OfflinePlayer player = plugin.getServer().getOfflinePlayer(playerUuid);
        Object result = invokeEconomy("has", new Class<?>[]{OfflinePlayer.class, double.class}, player, amount);
        return result instanceof Boolean hasEnough && hasEnough;
    }

    @Override
    public boolean withdraw(UUID playerUuid, double amount) {
        if (!isAvailable() || amount <= 0) {
            return amount <= 0;
        }

        OfflinePlayer player = plugin.getServer().getOfflinePlayer(playerUuid);
        Object response = invokeEconomy("withdrawPlayer", new Class<?>[]{OfflinePlayer.class, double.class}, player, amount);
        if (response == null) {
            return false;
        }

        try {
            Method transactionSuccessMethod = response.getClass().getMethod("transactionSuccess");
            Object success = transactionSuccessMethod.invoke(response);
            return success instanceof Boolean boolResult && boolResult;
        } catch (ReflectiveOperationException ex) {
            return false;
        }
    }

    public static boolean isVaultInstalled(JavaPlugin plugin) {
        Plugin detectedVault = plugin.getServer().getPluginManager().getPlugin(VAULT_PLUGIN_NAME);
        return detectedVault != null && detectedVault.isEnabled();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object resolveEconomy(JavaPlugin owner) {
        if (vaultPlugin == null || !vaultPlugin.isEnabled()) {
            return null;
        }

        try {
            Class economyClass = Class.forName(ECONOMY_CLASS_NAME);
            RegisteredServiceProvider registration = owner.getServer()
                    .getServicesManager()
                    .getRegistration(economyClass);
            if (registration == null) {
                return null;
            }

            Object provider = registration.getProvider();
            return economyClass.isInstance(provider) ? provider : null;
        } catch (ClassNotFoundException ex) {
            return null;
        }
    }

    private Object invokeEconomy(String methodName, Class<?>[] parameterTypes, Object... args) {
        try {
            Method method = economy.getClass().getMethod(methodName, parameterTypes);
            return method.invoke(economy, args);
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }
}
