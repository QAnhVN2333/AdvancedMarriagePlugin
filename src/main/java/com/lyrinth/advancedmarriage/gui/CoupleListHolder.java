package com.lyrinth.advancedmarriage.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class CoupleListHolder implements InventoryHolder {
    private final int page;

    public CoupleListHolder(int page) {
        this.page = page;
    }

    public int getPage() {
        return page;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}

