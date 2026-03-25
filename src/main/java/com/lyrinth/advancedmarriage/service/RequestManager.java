package com.lyrinth.advancedmarriage.service;

import com.lyrinth.advancedmarriage.model.MarriageRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RequestManager {
    private final Map<UUID, Map<UUID, MarriageRequest>> requestsByReceiver = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> outgoingReceiverBySender = new ConcurrentHashMap<>();

    public synchronized MarriageRequest create(UUID sender, UUID receiver, int timeoutSeconds) {
        // Replace stale outgoing request from the same sender before creating a new one.
        cancelOutgoing(sender);

        MarriageRequest request = new MarriageRequest(sender, receiver, Instant.now().plusSeconds(timeoutSeconds));
        requestsByReceiver
                .computeIfAbsent(receiver, ignored -> new ConcurrentHashMap<>())
                .put(sender, request);
        outgoingReceiverBySender.put(sender, receiver);
        return request;
    }

    public synchronized List<MarriageRequest> getIncoming(UUID receiver) {
        purgeExpired();
        Map<UUID, MarriageRequest> incoming = requestsByReceiver.get(receiver);
        if (incoming == null || incoming.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(incoming.values());
    }

    public synchronized Optional<MarriageRequest> getIncoming(UUID receiver, UUID sender) {
        purgeExpired();
        Map<UUID, MarriageRequest> incoming = requestsByReceiver.get(receiver);
        if (incoming == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(incoming.get(sender));
    }

    public synchronized Optional<MarriageRequest> getPendingOutgoing(UUID sender) {
        purgeExpired();
        UUID receiver = outgoingReceiverBySender.get(sender);
        if (receiver == null) {
            return Optional.empty();
        }
        return getIncoming(receiver, sender);
    }

    public synchronized boolean hasPendingOutgoing(UUID sender) {
        return getPendingOutgoing(sender).isPresent();
    }

    public synchronized boolean remove(UUID receiver, UUID sender) {
        Map<UUID, MarriageRequest> incoming = requestsByReceiver.get(receiver);
        if (incoming == null) {
            return false;
        }

        MarriageRequest removed = incoming.remove(sender);
        if (incoming.isEmpty()) {
            requestsByReceiver.remove(receiver);
        }
        if (removed != null) {
            outgoingReceiverBySender.remove(sender);
            return true;
        }
        return false;
    }

    public synchronized boolean cancelOutgoing(UUID sender) {
        UUID receiver = outgoingReceiverBySender.remove(sender);
        if (receiver == null) {
            return false;
        }
        Map<UUID, MarriageRequest> incoming = requestsByReceiver.get(receiver);
        if (incoming == null) {
            return false;
        }

        MarriageRequest removed = incoming.remove(sender);
        if (incoming.isEmpty()) {
            requestsByReceiver.remove(receiver);
        }
        return removed != null;
    }

    public synchronized void removeAllIncoming(UUID receiver) {
        Map<UUID, MarriageRequest> removed = requestsByReceiver.remove(receiver);
        if (removed == null) {
            return;
        }
        for (UUID sender : removed.keySet()) {
            outgoingReceiverBySender.remove(sender);
        }
    }

    public synchronized void clearRequestsForPlayer(UUID playerUuid) {
        removeAllIncoming(playerUuid);
        cancelOutgoing(playerUuid);
    }

    private void purgeExpired() {
        List<UUID> receiversToRemove = new ArrayList<>();

        for (Map.Entry<UUID, Map<UUID, MarriageRequest>> entry : requestsByReceiver.entrySet()) {
            UUID receiver = entry.getKey();
            Map<UUID, MarriageRequest> incoming = entry.getValue();

            incoming.entrySet().removeIf(incomingEntry -> {
                MarriageRequest request = incomingEntry.getValue();
                if (!request.isExpired()) {
                    return false;
                }
                outgoingReceiverBySender.remove(request.getSender());
                return true;
            });

            if (incoming.isEmpty()) {
                receiversToRemove.add(receiver);
            }
        }

        for (UUID receiver : receiversToRemove) {
            requestsByReceiver.remove(receiver);
        }
    }
}
