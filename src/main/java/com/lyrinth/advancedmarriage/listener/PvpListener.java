package com.lyrinth.advancedmarriage.listener;

import com.lyrinth.advancedmarriage.model.Marriage;
import com.lyrinth.advancedmarriage.service.ConfigService;
import com.lyrinth.advancedmarriage.service.MarriageService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.Optional;

public class PvpListener implements Listener {
    private final MarriageService marriageService;
    private final ConfigService configService;

    public PvpListener(MarriageService marriageService, ConfigService configService) {
        this.marriageService = marriageService;
        this.configService = configService;
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !(event.getDamager() instanceof Player attacker)) {
            return;
        }

        if (!configService.isFeatureEnabled("pvp_toggle")) {
            return;
        }

        Optional<Marriage> marriageOptional = marriageService.getMarriage(attacker.getUniqueId());
        if (marriageOptional.isEmpty()) {
            return;
        }

        Marriage marriage = marriageOptional.get();
        if (!marriage.includes(victim.getUniqueId())) {
            return;
        }

        if (!marriage.isPvpEnabled()) {
            event.setCancelled(true);
        }
    }
}

