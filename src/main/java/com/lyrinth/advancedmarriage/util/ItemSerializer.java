package com.lyrinth.advancedmarriage.util;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

public final class ItemSerializer {
    private ItemSerializer() {
    }

    public static String toBase64(ItemStack[] items) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
            dataOutput.writeInt(items.length);
            for (ItemStack item : items) {
                dataOutput.writeObject(item);
            }
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        }
    }

    public static ItemStack[] fromBase64(String base64, int size) throws IOException {
        if (base64 == null || base64.isBlank()) {
            return new ItemStack[size];
        }

        byte[] bytes = Base64.getDecoder().decode(base64);
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
            int itemCount = dataInput.readInt();
            ItemStack[] items = new ItemStack[Math.max(size, itemCount)];
            for (int i = 0; i < itemCount; i++) {
                try {
                    items[i] = (ItemStack) dataInput.readObject();
                } catch (ClassNotFoundException e) {
                    throw new IOException("Failed to deserialize chest item", e);
                }
            }
            return items;
        }
    }

    public static Inventory deserializeInventory(String base64, int size, String title) throws IOException {
        Inventory inventory = Bukkit.createInventory(null, size, title);
        inventory.setContents(fromBase64(base64, size));
        return inventory;
    }
}

