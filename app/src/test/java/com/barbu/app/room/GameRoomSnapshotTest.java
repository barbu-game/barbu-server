package com.barbu.app.room;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.barbu.engine.variant.Variants;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.Test;

class GameRoomSnapshotTest {

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final ObjectMapper mapper = new ObjectMapper();
    private final SnapshotCodec codec = new SnapshotCodec(new ObjectMapper());

    // Large bot delay so no bot move races the snapshot read; reconnectIndex null to decouple from Task 4.
    private GameRoom newRoom(String id) {
        return new GameRoom(
                id, 4, Variants.DEVELOPER, mapper, scheduler, 3_600_000L, null, null, "casual", null, null, 60000, 2);
    }

    @Test
    void snapshot_captures_seats_and_match_then_restores() {
        GameRoom room = newRoom("ROOM1");
        room.addHuman(null, "Alice", 7L);
        while (room.addBot()) {}
        room.start(99L);

        GameSnapshot snap = room.snapshot();
        assertEquals("ROOM1", snap.roomId());
        assertEquals(4, snap.playerCount());
        assertEquals("Alice", snap.names()[0]);

        GameRoom restored =
                GameRoom.fromSnapshot(snap, mapper, scheduler, 3_600_000L, null, null, null, null, 60000, 2);
        GameSnapshot again = restored.snapshot();

        assertArrayEquals(snap.names(), again.names());
        assertArrayEquals(snap.isBot(), again.isBot());
        // Full state survives the round-trip (compared via canonical JSON, robust to int[] fields).
        assertEquals(codec.encode(snap), codec.encode(again));
        // Bots carry no account id.
        assertNull(again.userIds()[1]);
    }
}
