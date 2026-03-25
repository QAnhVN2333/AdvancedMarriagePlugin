package com.lyrinth.advancedmarriage.service;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TeleportService {
    private final JavaPlugin plugin;
    private final ConfigService configService;
    private final MessageService messageService;
    private final Map<UUID, PendingTeleport> pendingTeleports = new ConcurrentHashMap<>();

    public TeleportService(JavaPlugin plugin, ConfigService configService, MessageService messageService) {
        this.plugin = plugin;
        this.configService = configService;
        this.messageService = messageService;
    }

    public void teleportWithCountdown(Player player, Location location, Runnable onTeleport) {
        cancelTeleport(player.getUniqueId(), null);

        int countdown = Math.max(0, configService.getTeleportCountdownSeconds());
        Sound countdownSound = configService.getCountdownSound();
        Sound teleportSound = configService.getTeleportSound();

        if (countdown == 0) {
            player.teleport(location);
            if (teleportSound != null) {
                player.playSound(player.getLocation(), teleportSound, 1f, 1f);
            }
            onTeleport.run();
            return;
        }

        UUID playerUuid = player.getUniqueId();
        Location startLocation = player.getLocation().clone();

        BukkitRunnable runnable = new BukkitRunnable() {
            int remaining = countdown;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    removePending(playerUuid);
                    cancel();
                    return;
                }

                if (remaining <= 0) {
                    player.teleport(location);
                    if (teleportSound != null) {
                        player.playSound(player.getLocation(), teleportSound, 1f, 1f);
                    }
                    onTeleport.run();
                    removePending(playerUuid);
                    cancel();
                    return;
                }

                messageService.send(player, "tp.countdown", Map.of("seconds", String.valueOf(remaining)));
                if (countdownSound != null) {
                    player.playSound(player.getLocation(), countdownSound, 1f, 1f);
                }
                remaining--;
            }
        };

        pendingTeleports.put(playerUuid, new PendingTeleport(runnable, startLocation));
        runnable.runTaskTimer(plugin, 0L, 20L);
    }

    public boolean hasPendingTeleport(UUID playerUuid) {
        return pendingTeleports.containsKey(playerUuid);
    }

    public boolean cancelTeleport(UUID playerUuid, String cancelMessagePath) {
        PendingTeleport pendingTeleport = removePending(playerUuid);
        if (pendingTeleport == null) {
            return false;
        }

        pendingTeleport.runnable.cancel();
        Player player = plugin.getServer().getPlayer(playerUuid);
        if (player != null && cancelMessagePath != null && !cancelMessagePath.isBlank()) {
            messageService.send(player, cancelMessagePath);
        }
        return true;
    }

    public boolean shouldCancelOnMove(Player player, Location to) {
        PendingTeleport pendingTeleport = pendingTeleports.get(player.getUniqueId());
        if (pendingTeleport == null || to == null || to.getWorld() == null) {
            return false;
        }

        Location origin = pendingTeleport.origin;
        if (origin.getWorld() == null || !origin.getWorld().equals(to.getWorld())) {
            return true;
        }

        return origin.getBlockX() != to.getBlockX()
                || origin.getBlockY() != to.getBlockY()
                || origin.getBlockZ() != to.getBlockZ();
    }

    private PendingTeleport removePending(UUID playerUuid) {
        return pendingTeleports.remove(playerUuid);
    }

    private record PendingTeleport(BukkitRunnable runnable, Location origin) {
    }
}
