package com.lyrinth.advancedmarriage.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class SharedChestHolder implements InventoryHolder {
    private final long marriageId;
    private Inventory inventory;

    public SharedChestHolder(long marriageId) {
        this.marriageId = marriageId;
    }

    public long getMarriageId() {
        return marriageId;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
