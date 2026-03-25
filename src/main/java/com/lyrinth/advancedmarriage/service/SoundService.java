package com.lyrinth.advancedmarriage.service;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class SoundService {
    public static final String GUI_CLICK = "gui_click";
    public static final String REQUEST_RECEIVED = "request_received";
    public static final String REQUEST_SENT = "request_sent";
    public static final String MARRY_ACCEPTED = "marry_accepted";
    public static final String MARRY_REJECTED = "marry_rejected";
    public static final String FEATURE_TOGGLED = "feature_toggled";
    public static final String MARRY_CHAT_MESSAGE = "marry_chat_message";
    public static final String BLOCKED = "blocked";

    private final ConfigService configService;

    public SoundService(ConfigService configService) {
        this.configService = configService;
    }

    public void play(Player player, String key) {
        if (player == null || key == null || key.isBlank()) {
            return;
        }

        // Keep runtime safe when config uses "none" or an invalid sound token.
        Sound sound = configService.getSoundByKey(key);
        if (sound == null) {
            return;
        }

        player.playSound(player.getLocation(), sound, 1f, 1f);
    }
}

