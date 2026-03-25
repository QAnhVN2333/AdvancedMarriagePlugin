package com.lyrinth.advancedmarriage.listener;

import com.lyrinth.advancedmarriage.service.MarriageService;
import com.lyrinth.advancedmarriage.service.MessageService;
import com.lyrinth.advancedmarriage.service.PreferenceService;
import com.lyrinth.advancedmarriage.service.SoundService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class ChatListener implements Listener {
    private final MarriageService marriageService;
    private final PreferenceService preferenceService;
    private final MessageService messageService;
    private final SoundService soundService;

    public ChatListener(
            MarriageService marriageService,
            PreferenceService preferenceService,
            MessageService messageService,
            SoundService soundService
    ) {
        this.marriageService = marriageService;
        this.preferenceService = preferenceService;
        this.messageService = messageService;
        this.soundService = soundService;
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player sender = event.getPlayer();
        if (!preferenceService.isPartnerChatEnabled(sender.getUniqueId())) {
            return;
        }

        Optional<UUID> partnerUuidOptional = marriageService.getPartner(sender.getUniqueId());
        if (partnerUuidOptional.isEmpty()) {
            return;
        }

        Player partner = Bukkit.getPlayer(partnerUuidOptional.get());
        if (partner == null) {
            return;
        }

        event.setCancelled(true);
        String formatted = messageService.renderLegacy("chat.partner_format", Map.of(
                "name", sender.getName(),
                "message", event.getMessage()
        ));
        sender.sendMessage(formatted);
        partner.sendMessage(formatted);
        soundService.play(partner, SoundService.MARRY_CHAT_MESSAGE);
    }
}
