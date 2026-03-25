package com.lyrinth.advancedmarriage.listener;

import com.lyrinth.advancedmarriage.gui.CoupleListHolder;
import com.lyrinth.advancedmarriage.gui.CoupleListGui;
import com.lyrinth.advancedmarriage.gui.SharedChestHolder;
import com.lyrinth.advancedmarriage.service.ChestSessionService;
import com.lyrinth.advancedmarriage.service.MessageService;
import com.lyrinth.advancedmarriage.service.SoundService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import java.io.IOException;
import java.sql.SQLException;

public class InventoryListener implements Listener {
    private final CoupleListGui coupleListGui;
    private final ChestSessionService chestSessionService;
    private final MessageService messageService;
    private final SoundService soundService;

    public InventoryListener(
            CoupleListGui coupleListGui,
            ChestSessionService chestSessionService,
            MessageService messageService,
            SoundService soundService
    ) {
        this.coupleListGui = coupleListGui;
        this.chestSessionService = chestSessionService;
        this.messageService = messageService;
        this.soundService = soundService;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory inventory = event.getInventory();
        if (inventory.getHolder() instanceof SharedChestHolder) {
            if (!event.isCancelled()) {
                chestSessionService.markDirty(inventory);
            }
            return;
        }

        if (!(inventory.getHolder() instanceof CoupleListHolder holder)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int slot = event.getRawSlot();
        if (slot == 48) {
            soundService.play(player, SoundService.GUI_CLICK);
            coupleListGui.open(player, holder.getPage() - 1);
        } else if (slot == 50) {
            soundService.play(player, SoundService.GUI_CLICK);
            coupleListGui.open(player, holder.getPage() + 1);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory inventory = event.getInventory();
        if (!(inventory.getHolder() instanceof SharedChestHolder)) {
            return;
        }

        if (!event.isCancelled()) {
            chestSessionService.markDirty(inventory);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory inventory = event.getInventory();
        if (!(inventory.getHolder() instanceof SharedChestHolder)) {
            return;
        }

        try {
            chestSessionService.handleInventoryClose(inventory);
        } catch (SQLException | IOException ex) {
            if (event.getPlayer() instanceof Player player) {
                messageService.send(player, "chest.save_failed");
            }
        }
    }
}
