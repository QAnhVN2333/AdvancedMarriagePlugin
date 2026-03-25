package com.lyrinth.advancedmarriage.service;

import com.lyrinth.advancedmarriage.model.MarriageRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestManagerTest {

    @Test
    void shouldCreateAndReadIncomingRequest() {
        RequestManager manager = new RequestManager();
        UUID sender = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();

        manager.create(sender, receiver, 60);

        List<MarriageRequest> incoming = manager.getIncoming(receiver);
        assertEquals(1, incoming.size());
        assertTrue(manager.getIncoming(receiver, sender).isPresent());
    }

    @Test
    void shouldSupportMultipleSendersForSameReceiver() {
        RequestManager manager = new RequestManager();
        UUID receiver = UUID.randomUUID();

        manager.create(UUID.randomUUID(), receiver, 60);
        manager.create(UUID.randomUUID(), receiver, 60);

        assertEquals(2, manager.getIncoming(receiver).size());
    }

    @Test
    void shouldTrackAndCancelOutgoingRequest() {
        RequestManager manager = new RequestManager();
        UUID sender = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();

        manager.create(sender, receiver, 60);

        assertTrue(manager.hasPendingOutgoing(sender));
        assertTrue(manager.cancelOutgoing(sender));
        assertFalse(manager.hasPendingOutgoing(sender));
        assertTrue(manager.getIncoming(receiver).isEmpty());
    }

    @Test
    void shouldExpireRequest() throws InterruptedException {
        RequestManager manager = new RequestManager();
        UUID sender = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();

        manager.create(sender, receiver, 1);
        Thread.sleep(1200);

        assertFalse(manager.getIncoming(receiver, sender).isPresent());
        assertFalse(manager.hasPendingOutgoing(sender));
    }
}
