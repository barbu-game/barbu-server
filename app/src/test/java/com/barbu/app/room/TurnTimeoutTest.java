package com.barbu.app.room;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.barbu.app.protocol.GameStateMessage;
import com.barbu.app.protocol.GameStateMessage.PlayerInfo;
import com.barbu.engine.variant.Variants;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.Test;

class TurnTimeoutTest {

    private static PlayerInfo player(GameRoom room, int seat) {
        return room.viewFor(0).players().get(seat);
    }

    private static GameRoom room(ScheduledExecutorService scheduler, long timeoutMs, int strikes) {
        return new GameRoom(
                "T",
                3,
                Variants.DEVELOPER,
                new ObjectMapper(),
                scheduler,
                0,
                null,
                null,
                "private",
                null,
                null,
                timeoutMs,
                strikes);
    }

    @Test
    void a_disconnected_human_is_not_botted_on_disconnect_but_after_two_timeouts() throws InterruptedException {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
        try {
            GameRoom room = room(scheduler, 40, 2);
            int seat = room.addHuman(new FakeSession(), "Alice", 1L);
            assertEquals(0, seat);
            assertTrue(room.addBot());
            assertTrue(room.addBot());
            assertTrue(room.start(42L));

            room.handleDisconnect(seat);
            assertFalse(player(room, seat).bot(), "disconnect alone must not bot the seat");

            long deadline = System.currentTimeMillis() + 4000;
            while (!player(room, seat).bot() && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            assertTrue(player(room, seat).bot(), "after two consecutive timeouts the seat is handed to a bot");
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void the_active_human_seat_publishes_an_absolute_turn_deadline() throws InterruptedException {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
        try {
            GameRoom room = room(scheduler, 60000, 2);
            room.addHuman(new FakeSession(), "Alice", 1L);
            room.addBot();
            room.addBot();
            room.start(42L);

            long deadline = System.currentTimeMillis() + 2000;
            Object epoch = null;
            while (epoch == null && System.currentTimeMillis() < deadline) {
                GameStateMessage view = room.viewFor(0);
                if (Integer.valueOf(0).equals(view.currentActor())) {
                    epoch = view.turnDeadlineEpochMs();
                }
                if (epoch == null) {
                    Thread.sleep(20);
                }
            }
            assertTrue(
                    epoch instanceof Long && (Long) epoch > System.currentTimeMillis(),
                    "the active human's seat must carry a future turnDeadlineEpochMs");
        } finally {
            scheduler.shutdownNow();
        }
    }
}
