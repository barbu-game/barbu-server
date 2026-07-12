package com.barbu.app.room;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.barbu.app.cluster.InMemoryRoomRegistry;
import com.barbu.app.cluster.InMemorySnapshotStore;
import com.barbu.app.cluster.RoomRegistry;
import com.barbu.engine.variant.Variants;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class RoomManagerDrainTest {

    @Test
    void release_all_leases_frees_every_owned_room() {
        RoomRegistry registry = new InMemoryRoomRegistry();
        RoomManager mgr = new RoomManager(
                new ObjectMapper(),
                null,
                null,
                null,
                new InMemoryReconnectIndex(),
                registry,
                new InMemorySnapshotStore(),
                new SnapshotCodec(new ObjectMapper()),
                "pod-0",
                8,
                60000,
                2,
                90000);
        GameRoom a = mgr.create(4, Variants.DEVELOPER, "casual");
        GameRoom b = mgr.create(4, Variants.DEVELOPER, "casual");

        mgr.releaseAllLeases();

        assertTrue(registry.ownerOf(a.id()).isEmpty());
        assertTrue(registry.ownerOf(b.id()).isEmpty());
    }
}
