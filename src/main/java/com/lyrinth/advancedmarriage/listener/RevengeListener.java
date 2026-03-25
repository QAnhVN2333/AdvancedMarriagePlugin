package com.lyrinth.advancedmarriage.listener;

import com.lyrinth.advancedmarriage.service.MarriageService;
import com.lyrinth.advancedmarriage.service.MessageService;
import com.lyrinth.advancedmarriage.service.RevengeService;
import com.lyrinth.advancedmarriage.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class RevengeListener implements Listener {
    private final MarriageService marriageService;
    private final RevengeService revengeService;
    private final MessageService messageService;

    public RevengeListener(MarriageService marriageService, RevengeService revengeService, MessageService messageService) {
        this.marriageService = marriageService;
        this.revengeService = revengeService;
        this.messageService = messageService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }

        // Disable all revenge flows in blocked worlds.
        if (!revengeService.isWorldAllowed(victim.getWorld().getName())
                || !revengeService.isWorldAllowed(killer.getWorld().getName())) {
            return;
        }

        createRevengeForPartner(victim, killer);
        completeRevengeIfNeeded(victim, killer);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !(event.getDamager() instanceof Player attacker)) {
            return;
        }

        if (!revengeService.isWorldAllowed(victim.getWorld().getName())
                || !revengeService.isWorldAllowed(attacker.getWorld().getName())) {
            return;
        }

        if (revengeService.hasActiveRevenge(attacker.getUniqueId(), victim.getUniqueId())) {
            revengeService.applyBuffs(attacker, victim);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        revengeService.trackPlayerJoin(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        revengeService.trackPlayerQuit(event.getPlayer().getUniqueId());
    }

    private void createRevengeForPartner(Player victim, Player killer) {
        Optional<UUID> partnerUuid = marriageService.getPartner(victim.getUniqueId());
        if (partnerUuid.isEmpty()) {
            return;
        }

        if (partnerUuid.get().equals(killer.getUniqueId())) {
            return;
        }

        int points = revengeService.grantRevenge(partnerUuid.get(), killer.getUniqueId());
        if (points <= 0) {
            return;
        }

        Player onlinePartner = Bukkit.getPlayer(partnerUuid.get());
        if (onlinePartner == null) {
            return;
        }

        messageService.send(onlinePartner, "revenge.point_added", Map.of(
                "killer", killer.getName(),
                "points", String.valueOf(points)
        ));
    }

    private void completeRevengeIfNeeded(Player victim, Player killer) {
        int revengePoints = revengeService.completeRevenge(killer.getUniqueId(), victim.getUniqueId());
        if (revengePoints <= 0) {
            return;
        }

        messageService.send(killer, "revenge.completed", Map.of(
                "killer", victim.getName(),
                "points", String.valueOf(revengePoints)
        ));

        for (String command : revengeService.getRewardCommands()) {
            String resolved = applyPlaceholders(command, killer.getName(), victim.getName(), revengePoints);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
        }

        List<String> rewardMessages = revengeService.getRewardMessages();
        for (String rewardMessage : rewardMessages) {
            String resolved = applyPlaceholders(rewardMessage, killer.getName(), victim.getName(), revengePoints);
            killer.sendMessage(ColorUtil.colorize(resolved));
        }
    }

    private String applyPlaceholders(String input, String revengerName, String killerName, int points) {
        return input
                .replace("%player%", revengerName)
                .replace("%killer%", killerName)
                .replace("%points%", String.valueOf(points));
    }
}
