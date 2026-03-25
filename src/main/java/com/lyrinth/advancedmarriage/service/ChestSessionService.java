package com.lyrinth.advancedmarriage.service;

import com.lyrinth.advancedmarriage.gui.SharedChestHolder;
import org.bukkit.inventory.Inventory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

public class ChestSessionService {
    private final ChestService chestService;
    private final ConfigService configService;
    private final Map<Long, ChestSession> sessions = new HashMap<>();

    public ChestSessionService(ChestService chestService, ConfigService configService) {
        this.chestService = chestService;
        this.configService = configService;
    }

    public synchronized Inventory openInventory(long marriageId, String title, int defaultSize)
            throws SQLException, IOException, ChestBusyException {
        ChestSession session = sessions.get(marriageId);
        if (session != null) {
            // Reuse the exact same inventory instance so both partners see live updates.
            session.incrementViewers();
            return session.getInventory();
        }

        if (!configService.isCrossServerChestEnabled()) {
            Inventory inventory = chestService.loadInventory(marriageId, title, defaultSize);
            sessions.put(marriageId, new ChestSession(inventory));
            return inventory;
        }

        String serverId = configService.getServerId();
        long ttlMillis = configService.getChestLockTtlSeconds() * 1000L;
        Optional<ChestService.ChestLease> leaseOptional = chestService.tryAcquireLease(
                marriageId,
                serverId,
                defaultSize,
                ttlMillis,
                true
        );
        if (leaseOptional.isEmpty()) {
            throw new ChestBusyException("Shared chest is currently in use by another server.");
        }

        ChestService.ChestLease lease = leaseOptional.get();
        ChestService.ChestSnapshot snapshot = chestService.loadInventorySnapshot(marriageId, title, defaultSize, lease.token());

        ChestSession lockAwareSession = new ChestSession(snapshot.inventory(), lease.token(), snapshot.version(), ttlMillis);
        sessions.put(marriageId, lockAwareSession);
        return snapshot.inventory();
    }

    public synchronized void markDirty(Inventory inventory) {
        if (!(inventory.getHolder() instanceof SharedChestHolder holder)) {
            return;
        }

        ChestSession session = sessions.get(holder.getMarriageId());
        if (session == null || session.getInventory() != inventory) {
            return;
        }
        session.setDirty(true);
    }

    public synchronized void handleInventoryClose(Inventory inventory) throws SQLException, IOException {
        if (!(inventory.getHolder() instanceof SharedChestHolder holder)) {
            return;
        }

        long marriageId = holder.getMarriageId();
        ChestSession session = sessions.get(marriageId);
        if (session == null || session.getInventory() != inventory) {
            // Cross-server mode requires a valid lease, so skip unsafe fallback writes.
            if (!configService.isCrossServerChestEnabled()) {
                chestService.saveInventory(marriageId, configService.getServerId(), inventory);
            }
            return;
        }

        if (session.decrementViewers() > 0) {
            return;
        }

        try {
            persistSession(marriageId, session, true);
        } finally {
            sessions.remove(marriageId);
        }
    }

    public synchronized Map<Long, Exception> flushAllSessions() {
        Map<Long, Exception> saveFailures = new LinkedHashMap<>();

        for (Map.Entry<Long, ChestSession> entry : sessions.entrySet()) {
            long marriageId = entry.getKey();
            ChestSession session = entry.getValue();
            try {
                persistSession(marriageId, session, true);
            } catch (Exception ex) {
                saveFailures.put(marriageId, ex);
            }
        }

        sessions.clear();
        return saveFailures;
    }

    public synchronized Map<Long, Exception> tickSessions() {
        Map<Long, Exception> failures = new LinkedHashMap<>();
        if (!configService.isCrossServerChestEnabled()) {
            return failures;
        }

        long now = System.currentTimeMillis();
        for (Map.Entry<Long, ChestSession> entry : sessions.entrySet()) {
            long marriageId = entry.getKey();
            ChestSession session = entry.getValue();
            if (!session.isLockAware() || session.isInvalid()) {
                continue;
            }

            try {
                if (now >= session.getNextHeartbeatAt()) {
                    boolean renewed = chestService.renewLease(
                            marriageId,
                            configService.getServerId(),
                            session.getLockToken(),
                            session.getTtlMillis()
                    );
                    if (!renewed) {
                        session.setInvalid(true);
                        failures.put(marriageId, new IOException("Lost shared chest lease while renewing."));
                        continue;
                    }
                    session.setNextHeartbeatAt(now + session.getTtlMillis() / 2L);
                }

                if (session.isDirty() && now >= session.getNextCheckpointAt()) {
                    persistSession(marriageId, session, false);
                    session.setNextCheckpointAt(now + (configService.getChestCheckpointSeconds() * 1000L));
                }
            } catch (Exception ex) {
                session.setInvalid(true);
                failures.put(marriageId, ex);
            }
        }

        return failures;
    }

    private void persistSession(long marriageId, ChestSession session, boolean releaseLease) throws SQLException, IOException {
        if (!session.isLockAware()) {
            chestService.saveInventory(marriageId, configService.getServerId(), session.getInventory());
            return;
        }

        try {
            if (!session.isInvalid()) {
                OptionalLong newVersionOptional = chestService.saveWithVersion(
                        marriageId,
                        configService.getServerId(),
                        session.getInventory(),
                        session.getVersion(),
                        session.getLockToken()
                );
                if (newVersionOptional.isEmpty()) {
                    session.setInvalid(true);
                    throw new IOException("Shared chest version conflict detected.");
                }
                session.setVersion(newVersionOptional.getAsLong());
                session.setDirty(false);
            }
        } finally {
            if (releaseLease) {
                chestService.releaseLease(marriageId, session.getLockToken());
            }
        }
    }

    private static final class ChestSession {
        private final Inventory inventory;
        private final boolean lockAware;
        private final String lockToken;
        private final long ttlMillis;
        private int viewers;
        private long version;
        private boolean dirty;
        private boolean invalid;
        private long nextCheckpointAt;
        private long nextHeartbeatAt;

        private ChestSession(Inventory inventory) {
            this.inventory = inventory;
            this.lockAware = false;
            this.lockToken = null;
            this.ttlMillis = 0L;
            this.viewers = 1;
            this.version = 0L;
            this.dirty = true;
            this.invalid = false;
            this.nextCheckpointAt = 0L;
            this.nextHeartbeatAt = 0L;
        }

        private ChestSession(Inventory inventory, String lockToken, long version, long ttlMillis) {
            this.inventory = inventory;
            this.lockAware = true;
            this.lockToken = lockToken;
            this.ttlMillis = ttlMillis;
            this.viewers = 1;
            this.version = version;
            this.dirty = true;
            this.invalid = false;
            long now = System.currentTimeMillis();
            this.nextCheckpointAt = now;
            this.nextHeartbeatAt = now + (ttlMillis / 2L);
        }

        private Inventory getInventory() {
            return inventory;
        }

        private boolean isLockAware() {
            return lockAware;
        }

        private String getLockToken() {
            return lockToken;
        }

        private long getTtlMillis() {
            return ttlMillis;
        }

        private long getVersion() {
            return version;
        }

        private void setVersion(long version) {
            this.version = version;
        }

        private boolean isDirty() {
            return dirty;
        }

        private void setDirty(boolean dirty) {
            this.dirty = dirty;
        }

        private boolean isInvalid() {
            return invalid;
        }

        private void setInvalid(boolean invalid) {
            this.invalid = invalid;
        }

        private long getNextCheckpointAt() {
            return nextCheckpointAt;
        }

        private void setNextCheckpointAt(long nextCheckpointAt) {
            this.nextCheckpointAt = nextCheckpointAt;
        }

        private long getNextHeartbeatAt() {
            return nextHeartbeatAt;
        }

        private void setNextHeartbeatAt(long nextHeartbeatAt) {
            this.nextHeartbeatAt = nextHeartbeatAt;
        }

        private void incrementViewers() {
            viewers++;
        }

        private int decrementViewers() {
            viewers--;
            return viewers;
        }
    }
}
