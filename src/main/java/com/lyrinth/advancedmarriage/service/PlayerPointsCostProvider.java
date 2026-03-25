package com.lyrinth.advancedmarriage.service;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.UUID;

public class PlayerPointsCostProvider implements MarriageCostProvider {
    private final Plugin playerPointsPlugin;
    private final Object api;

    public PlayerPointsCostProvider(JavaPlugin plugin) {
        this.playerPointsPlugin = plugin.getServer().getPluginManager().getPlugin("PlayerPoints");
        this.api = resolveApi(playerPointsPlugin);
    }

    @Override
    public String getCurrencyName() {
        return "PlayerPoints";
    }

    @Override
    public boolean isAvailable() {
        return playerPointsPlugin != null && playerPointsPlugin.isEnabled() && api != null;
    }

    @Override
    public boolean has(UUID playerUuid, double amount) {
        if (!isAvailable() || amount <= 0) {
            return amount <= 0;
        }

        int points = (int) Math.ceil(amount);
        Integer currentBalance = readBalance(playerUuid);
        return currentBalance != null && currentBalance >= points;
    }

    @Override
    public boolean withdraw(UUID playerUuid, double amount) {
        if (!isAvailable() || amount <= 0) {
            return amount <= 0;
        }

        int points = (int) Math.ceil(amount);
        return invokeTake(playerUuid, points);
    }

    private Object resolveApi(Plugin plugin) {
        if (plugin == null) {
            return null;
        }
        try {
            Method method = plugin.getClass().getMethod("getAPI");
            return method.invoke(plugin);
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    private Integer readBalance(UUID playerUuid) {
        Integer byUuid = invokeInt(api, "look", UUID.class, playerUuid);
        if (byUuid != null) {
            return byUuid;
        }
        return invokeInt(api, "getPoints", UUID.class, playerUuid);
    }

    private boolean invokeTake(UUID playerUuid, int points) {
        Boolean tookByUuid = invokeBoolean(api, "take", UUID.class, playerUuid, int.class, points);
        if (tookByUuid != null) {
            return tookByUuid;
        }

        try {
            Method method = api.getClass().getMethod("take", UUID.class, int.class);
            method.invoke(api, playerUuid, points);
            Integer afterTake = readBalance(playerUuid);
            return afterTake != null;
        } catch (ReflectiveOperationException ex) {
            return false;
        }
    }

    private Integer invokeInt(Object target, String methodName, Class<?> argType, Object arg) {
        try {
            Method method = target.getClass().getMethod(methodName, argType);
            Object result = method.invoke(target, arg);
            if (result instanceof Number number) {
                return number.intValue();
            }
            return null;
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    private Boolean invokeBoolean(Object target, String methodName, Class<?> firstArgType, Object firstArg, Class<?> secondArgType, Object secondArg) {
        try {
            Method method = target.getClass().getMethod(methodName, firstArgType, secondArgType);
            Object result = method.invoke(target, firstArg, secondArg);
            if (result instanceof Boolean boolResult) {
                return boolResult;
            }
            return null;
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }
}

