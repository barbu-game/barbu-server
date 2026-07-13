package com.barbu.app.room;

import static org.junit.jupiter.api.Assertions.*;

import com.barbu.engine.variant.Variants;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.Test;

class GameRoomReserveHumanTest {

    private GameRoom newRoom(int playerCount, ScheduledExecutorService scheduler) {
        return new GameRoom(
                "T",
                playerCount,
                Variants.DEVELOPER,
                new ObjectMapper(),
                scheduler,
                0,
                null, // recorder
                null, // metrics
                "casual",
                null, // ratingService (non requis : casual)
                new InMemoryReconnectIndex(),
                60000,
                2);
    }

    @Test
    void reserved_seat_is_reclaimable_by_its_token() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
        try {
            GameRoom room = newRoom(4, scheduler);
            String token = room.reserveHuman("Alice", 42L);
            assertNotNull(token, "a reserved seat returns its resume token");

            room.addBot();
            room.addBot();
            room.addBot();
            assertTrue(room.start(1234L), "a room with a reserved human seat + bots starts");

            int seat = room.reclaim(new FakeSession(), 42L, token);
            assertTrue(seat >= 0, "the human reconnects and reclaims the reserved seat by token");
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void a_reserved_seat_is_not_handed_to_a_new_joiner() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
        try {
            GameRoom room = newRoom(4, scheduler);
            room.reserveHuman("Alice", 42L); // prend le siège 0
            int seatForBob = room.addHuman(new FakeSession(), "Bob", 7L);
            assertNotEquals(0, seatForBob, "the reserved seat 0 stays reserved; Bob takes the next seat");
        } finally {
            scheduler.shutdownNow();
        }
    }
}
