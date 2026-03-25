package com.lyrinth.advancedmarriage.listener;

import com.lyrinth.advancedmarriage.service.ConfigService;
import com.lyrinth.advancedmarriage.service.MarriageService;
import com.lyrinth.advancedmarriage.service.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class PartnerStatusListener implements Listener {
    private final MarriageService marriageService;
    private final MessageService messageService;
    private final ConfigService configService;

    public PartnerStatusListener(MarriageService marriageService, MessageService messageService, ConfigService configService) {
        this.marriageService = marriageService;
        this.messageService = messageService;
        this.configService = configService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player joiningPlayer = event.getPlayer();
        Optional<UUID> partnerUuid = marriageService.getPartner(joiningPlayer.getUniqueId());
        if (partnerUuid.isEmpty()) {
            return;
        }

        Player onlinePartner = Bukkit.getPlayer(partnerUuid.get());
        if (onlinePartner == null) {
            return;
        }

        messageService.send(onlinePartner, "partner.online", Map.of("partner", joiningPlayer.getName()));
        Sound sound = configService.getPartnerOnlineSound();
        if (sound != null) {
            onlinePartner.playSound(onlinePartner.getLocation(), sound, 1f, 1f);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player quittingPlayer = event.getPlayer();
        Optional<UUID> partnerUuid = marriageService.getPartner(quittingPlayer.getUniqueId());
        if (partnerUuid.isEmpty()) {
            return;
        }

        Player onlinePartner = Bukkit.getPlayer(partnerUuid.get());
        if (onlinePartner == null) {
            return;
        }

        messageService.send(onlinePartner, "partner.offline", Map.of("partner", quittingPlayer.getName()));
        Sound sound = configService.getPartnerOfflineSound();
        if (sound != null) {
            onlinePartner.playSound(onlinePartner.getLocation(), sound, 1f, 1f);
        }
    }
}

