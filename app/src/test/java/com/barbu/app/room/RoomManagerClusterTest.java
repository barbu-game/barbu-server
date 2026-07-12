package com.barbu.app.room;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.barbu.app.cluster.InMemoryRoomRegistry;
import com.barbu.app.cluster.InMemorySnapshotStore;
import com.barbu.app.cluster.RoomRegistry;
import com.barbu.app.cluster.SnapshotStore;
import com.barbu.engine.variant.Variants;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class RoomManagerClusterTest {

    private RoomManager manager(RoomRegistry registry, SnapshotStore store) {
        return new RoomManager(
                new ObjectMapper(),
                null,
                null,
                null,
                new InMemoryReconnectIndex(),
                registry,
                store,
                new SnapshotCodec(new ObjectMapper()),
                "pod-0",
                8,
                60000,
                2,
                90000);
    }

    @Test
    void creating_a_room_claims_ownership_and_persists_a_snapshot() {
        RoomRegistry registry = new InMemoryRoomRegistry();
        SnapshotStore store = new InMemorySnapshotStore();
        RoomManager mgr = manager(registry, store);

        GameRoom room = mgr.create(4, Variants.DEVELOPER, "casual");

        assertEquals("pod-0", registry.ownerOf(room.id()).orElseThrow());
        assertTrue(store.load(room.id()).isPresent());
    }

    @Test
    void removing_a_room_releases_lease_and_deletes_snapshot() {
        RoomRegistry registry = new InMemoryRoomRegistry();
        SnapshotStore store = new InMemorySnapshotStore();
        RoomManager mgr = manager(registry, store);
        GameRoom room = mgr.create(4, Variants.DEVELOPER, "casual");
        String id = room.id();

        mgr.remove(id);

        assertTrue(registry.ownerOf(id).isEmpty());
        assertTrue(store.load(id).isEmpty());
    }
}
