package com.lyrinth.advancedmarriage.service;

import com.lyrinth.advancedmarriage.gui.SharedChestHolder;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChestSessionServiceTest {

    @Test
    void openInventoryShouldReuseSameInstanceForSameMarriage() throws Exception {
        ChestService chestService = mock(ChestService.class);
        ConfigService configService = mock(ConfigService.class);

        Inventory inventory = mockSharedInventory(10L);
        when(chestService.loadInventory(10L, "Shared Chest", 36)).thenReturn(inventory);

        ChestSessionService sessionService = new ChestSessionService(chestService, configService);
        Inventory firstOpen = sessionService.openInventory(10L, "Shared Chest", 36);
        Inventory secondOpen = sessionService.openInventory(10L, "Shared Chest", 36);

        assertSame(firstOpen, secondOpen);
        verify(chestService, times(1)).loadInventory(10L, "Shared Chest", 36);
    }

    @Test
    void shouldSaveOnlyWhenLastViewerCloses() throws Exception {
        ChestService chestService = mock(ChestService.class);
        ConfigService configService = mock(ConfigService.class);
        when(configService.getServerId()).thenReturn("survival-1");

        Inventory inventory = mockSharedInventory(22L);
        when(chestService.loadInventory(22L, "Shared Chest", 36)).thenReturn(inventory);

        ChestSessionService sessionService = new ChestSessionService(chestService, configService);
        sessionService.openInventory(22L, "Shared Chest", 36);
        sessionService.openInventory(22L, "Shared Chest", 36);

        sessionService.handleInventoryClose(inventory);
        verify(chestService, never()).saveInventory(22L, "survival-1", inventory);

        sessionService.handleInventoryClose(inventory);
        verify(chestService, times(1)).saveInventory(22L, "survival-1", inventory);

        sessionService.openInventory(22L, "Shared Chest", 36);
        verify(chestService, times(2)).loadInventory(22L, "Shared Chest", 36);
    }

    @Test
    void shouldFallbackSaveWhenSessionMissing() throws Exception {
        ChestService chestService = mock(ChestService.class);
        ConfigService configService = mock(ConfigService.class);
        when(configService.getServerId()).thenReturn("survival-1");

        ChestSessionService sessionService = new ChestSessionService(chestService, configService);
        Inventory inventory = mockSharedInventory(77L);

        sessionService.handleInventoryClose(inventory);

        verify(chestService, times(1)).saveInventory(77L, "survival-1", inventory);
    }

    @Test
    void flushAllSessionsShouldSaveEveryOpenInventory() throws Exception {
        ChestService chestService = mock(ChestService.class);
        ConfigService configService = mock(ConfigService.class);
        when(configService.getServerId()).thenReturn("survival-1");

        Inventory firstInventory = mockSharedInventory(10L);
        Inventory secondInventory = mockSharedInventory(20L);
        when(chestService.loadInventory(10L, "Shared Chest", 36)).thenReturn(firstInventory);
        when(chestService.loadInventory(20L, "Shared Chest", 36)).thenReturn(secondInventory);

        ChestSessionService sessionService = new ChestSessionService(chestService, configService);
        sessionService.openInventory(10L, "Shared Chest", 36);
        sessionService.openInventory(20L, "Shared Chest", 36);

        Map<Long, Exception> failures = sessionService.flushAllSessions();

        assertEquals(0, failures.size());
        verify(chestService, times(1)).saveInventory(10L, "survival-1", firstInventory);
        verify(chestService, times(1)).saveInventory(20L, "survival-1", secondInventory);
    }

    @Test
    void flushAllSessionsShouldContinueSavingWhenOneFails() throws Exception {
        ChestService chestService = mock(ChestService.class);
        ConfigService configService = mock(ConfigService.class);
        when(configService.getServerId()).thenReturn("survival-1");

        Inventory firstInventory = mockSharedInventory(10L);
        Inventory secondInventory = mockSharedInventory(20L);
        when(chestService.loadInventory(10L, "Shared Chest", 36)).thenReturn(firstInventory);
        when(chestService.loadInventory(20L, "Shared Chest", 36)).thenReturn(secondInventory);
        doThrow(new IOException("Disk error")).when(chestService).saveInventory(10L, "survival-1", firstInventory);

        ChestSessionService sessionService = new ChestSessionService(chestService, configService);
        sessionService.openInventory(10L, "Shared Chest", 36);
        sessionService.openInventory(20L, "Shared Chest", 36);

        Map<Long, Exception> failures = sessionService.flushAllSessions();

        assertEquals(1, failures.size());
        assertEquals(IOException.class, failures.get(10L).getClass());
        verify(chestService, times(1)).saveInventory(20L, "survival-1", secondInventory);
    }

    private Inventory mockSharedInventory(long marriageId) {
        SharedChestHolder holder = new SharedChestHolder(marriageId);
        Inventory inventory = mock(Inventory.class);
        when(inventory.getHolder()).thenReturn(holder);
        return inventory;
    }
}
