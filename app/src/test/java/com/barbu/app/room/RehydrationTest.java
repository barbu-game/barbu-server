package com.barbu.app.room;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.barbu.app.cluster.InMemoryRoomRegistry;
import com.barbu.app.cluster.InMemorySnapshotStore;
import com.barbu.app.cluster.RoomRegistry;
import com.barbu.app.cluster.SnapshotStore;
import com.barbu.engine.variant.Variants;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class RehydrationTest {

    // Shared registry + store stand in for shared Redis across two pods.
    private RoomManager pod(String podId, RoomRegistry reg, SnapshotStore store) {
        return new RoomManager(
                new ObjectMapper(),
                null,
                null,
                null,
                new InMemoryReconnectIndex(),
                reg,
                store,
                new SnapshotCodec(new ObjectMapper()),
                podId,
                3_600_000L,
                60000,
                2,
                90000);
    }

    @Test
    void dead_owner_leads_to_local_rehydration() {
        RoomRegistry reg = new InMemoryRoomRegistry();
        SnapshotStore store = new InMemorySnapshotStore();

        RoomManager pod0 = pod("pod-0", reg, store);
        GameRoom room = pod0.create(4, Variants.DEVELOPER, "casual");
        room.addHuman(null, "Alice", 7L);
        while (room.addBot()) {}
        room.start(123L);
        String id = room.id();
        // Simulate a crash of pod-0: its lease is gone but the durable snapshot survives.
        reg.release(id, "pod-0");

        RoomManager pod1 = pod("pod-1", reg, store);
        RoomManager.Resolved resolved = pod1.resolveOrRehydrate(id);

        assertEquals(RoomManager.Resolution.LOCAL, resolved.resolution());
        assertNotNull(resolved.room());
        assertEquals("pod-1", reg.ownerOf(id).orElseThrow());
        assertEquals("Alice", resolved.room().snapshot().names()[0]);
    }

    @Test
    void live_foreign_owner_leads_to_redirect() {
        RoomRegistry reg = new InMemoryRoomRegistry();
        SnapshotStore store = new InMemorySnapshotStore();
        RoomManager pod0 = pod("pod-0", reg, store);
        GameRoom room = pod0.create(4, Variants.DEVELOPER, "casual");
        String id = room.id();

        RoomManager pod1 = pod("pod-1", reg, store);
        RoomManager.Resolved resolved = pod1.resolveOrRehydrate(id);

        assertEquals(RoomManager.Resolution.REDIRECT, resolved.resolution());
        assertEquals("pod-0", resolved.ownerPod());
    }
}
