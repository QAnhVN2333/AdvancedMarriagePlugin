package com.lyrinth.advancedmarriage.gui;

import com.lyrinth.advancedmarriage.model.Marriage;
import com.lyrinth.advancedmarriage.service.MarriageService;
import com.lyrinth.advancedmarriage.service.MessageService;
import com.lyrinth.advancedmarriage.service.PreferenceService;
import com.lyrinth.advancedmarriage.util.PaginationUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CoupleListGui {
    private static final int PAGE_SIZE = 45;

    private final MarriageService marriageService;
    private final PreferenceService preferenceService;
    private final MessageService messageService;

    public CoupleListGui(MarriageService marriageService, PreferenceService preferenceService, MessageService messageService) {
        this.marriageService = marriageService;
        this.preferenceService = preferenceService;
        this.messageService = messageService;
    }

    public void open(Player player, int requestedPage) {
        List<Marriage> marriages = marriageService.getAllMarriages();
        int totalPages = PaginationUtil.totalPages(marriages.size(), PAGE_SIZE);
        int page = PaginationUtil.clampPage(requestedPage, totalPages);

        Inventory inventory = Bukkit.createInventory(new CoupleListHolder(page), 54, messageService.get("gui.couple_list_title"));

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, marriages.size());
        for (int i = start; i < end; i++) {
            Marriage marriage = marriages.get(i);
            ItemStack item = createMarriageHead(marriage);
            inventory.setItem(i - start, item);
        }

        inventory.setItem(48, createButton(Material.ARROW, messageService.get("gui.previous")));
        inventory.setItem(49, createButton(Material.PAPER, messageService.format("gui.page", Map.of(
                "current", String.valueOf(page + 1),
                "total", String.valueOf(totalPages)
        ))));
        inventory.setItem(50, createButton(Material.ARROW, messageService.get("gui.next")));

        player.openInventory(inventory);
    }

    private ItemStack createMarriageHead(Marriage marriage) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta rawMeta = head.getItemMeta();
        if (!(rawMeta instanceof SkullMeta meta)) {
            return head;
        }

        UUID displayUuid = resolveDisplayUuid(marriage);
        OfflinePlayer displayPlayer = Bukkit.getOfflinePlayer(displayUuid);
        meta.setOwningPlayer(displayPlayer);

        OfflinePlayer playerA = Bukkit.getOfflinePlayer(marriage.getPlayerA());
        OfflinePlayer playerB = Bukkit.getOfflinePlayer(marriage.getPlayerB());

        String date = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                .withZone(ZoneId.systemDefault())
                .format(marriage.getMarriedAt());

        meta.setDisplayName(messageService.format("gui.couple_name", Map.of(
                "player1", playerA.getName() == null ? marriage.getPlayerA().toString() : playerA.getName(),
                "player2", playerB.getName() == null ? marriage.getPlayerB().toString() : playerB.getName()
        )));

        Map<String, String> placeholders = Map.of(
                "player1", playerA.getName() == null ? "Unknown" : playerA.getName(),
                "player2", playerB.getName() == null ? "Unknown" : playerB.getName(),
                "date", date
        );

        List<String> lore = messageService.formatList("gui.couple_lore", placeholders);
        if (lore.isEmpty()) {
            // Backward-compatible fallback for older messages.yml versions.
            lore = List.of(
                    messageService.format("gui.couple_lore_1", placeholders),
                    messageService.format("gui.couple_lore_2", placeholders)
            );
        }

        meta.setLore(lore);
        head.setItemMeta(meta);
        return head;
    }

    private UUID resolveDisplayUuid(Marriage marriage) {
        UUID displayUuid = preferenceService.getDisplayUuid(marriage.getPlayerA());
        if (displayUuid == null) {
            displayUuid = preferenceService.getDisplayUuid(marriage.getPlayerB());
        }

        // Guard against stale/invalid values in preferences.
        if (displayUuid == null || !marriage.includes(displayUuid)) {
            return marriage.getPlayerA();
        }
        return displayUuid;
    }

    private ItemStack createButton(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }
}
