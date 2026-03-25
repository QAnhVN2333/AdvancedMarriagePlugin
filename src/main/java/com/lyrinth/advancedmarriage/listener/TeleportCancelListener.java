package com.lyrinth.advancedmarriage.listener;

import com.lyrinth.advancedmarriage.service.TeleportService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public class TeleportCancelListener implements Listener {
    private final TeleportService teleportService;

    public TeleportCancelListener(TeleportService teleportService) {
        this.teleportService = teleportService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!teleportService.hasPendingTeleport(player.getUniqueId())) {
            return;
        }

        if (teleportService.shouldCancelOnMove(player, event.getTo())) {
            teleportService.cancelTeleport(player.getUniqueId(), "tp.cancelled_move");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        teleportService.cancelTeleport(player.getUniqueId(), "tp.cancelled_damage");
    }
}

