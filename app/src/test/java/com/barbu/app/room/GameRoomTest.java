package com.barbu.app.room;

import static org.junit.jupiter.api.Assertions.*;

import com.barbu.app.protocol.GameStateMessage;
import com.barbu.engine.variant.Variants;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.Test;

class GameRoomTest {

    @Test
    void all_bot_table_plays_to_completion() throws InterruptedException {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            GameRoom room = new GameRoom("TEST1", 3, Variants.DEVELOPER, new ObjectMapper(), scheduler, 0, null, null);
            assertTrue(room.addBot());
            assertTrue(room.addBot());
            assertTrue(room.addBot());
            assertTrue(room.start(42L));

            long deadline = System.currentTimeMillis() + 5000;
            while (!"GAME_OVER".equals(room.viewFor(0).phase()) && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            assertEquals("GAME_OVER", room.viewFor(0).phase());
            assertNotNull(room.viewFor(0).standings());
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void guest_name_is_trimmed_and_clamped_to_the_limit() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            GameRoom room = new GameRoom("TESTN", 3, Variants.DEVELOPER, new ObjectMapper(), scheduler, 0, null, null);
            int seat = room.addHuman(new FakeSession(), "  " + "z".repeat(60) + "  ", null);
            assertEquals("z".repeat(40), room.viewFor(seat).players().get(seat).name());
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void start_is_refused_until_all_seats_are_filled() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            GameRoom room = new GameRoom("TEST2", 3, Variants.DEVELOPER, new ObjectMapper(), scheduler, 0, null, null);
            assertTrue(room.addBot());
            assertFalse(room.start(1L), "should not start with empty seats");
            assertEquals("LOBBY", room.viewFor(0).phase());
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void classic_variant_all_bot_table_completes() throws InterruptedException {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            GameRoom room = new GameRoom("TESTC", 3, Variants.CLASSIC, new ObjectMapper(), scheduler, 0, null, null);
            assertTrue(room.addBot());
            assertTrue(room.addBot());
            assertTrue(room.addBot());
            assertTrue(room.start(7L));
            long deadline = System.currentTimeMillis() + 8000;
            while (!"GAME_OVER".equals(room.viewFor(0).phase()) && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            assertEquals("GAME_OVER", room.viewFor(0).phase());
            assertEquals("classic", room.viewFor(0).variant().id());
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void captured_is_exposed_per_seat_during_trick_taking() throws InterruptedException {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            // A non-zero bot delay keeps trick-taking observable: with instant bot moves the whole
            // round can elapse between two polls, so the snapshot never catches a trick in progress.
            GameRoom room = new GameRoom("CAPTR", 3, Variants.DEVELOPER, new ObjectMapper(), scheduler, 8, null, null);
            assertTrue(room.addBot());
            assertTrue(room.addBot());
            assertTrue(room.addBot());
            assertTrue(room.start(42L));

            // Take a single consistent snapshot once a trick-taking round is in progress.
            GameStateMessage view = null;
            long deadline = System.currentTimeMillis() + 5000;
            while (System.currentTimeMillis() < deadline) {
                view = room.viewFor(0);
                if (view.trick() != null) {
                    break;
                }
                Thread.sleep(10);
            }
            assertNotNull(view.trick(), "expected a trick-taking round in progress");
            assertNotNull(view.captured(), "captured must be exposed during trick-taking");
            assertEquals(3, view.captured().size(), "captured is indexed by seat");
        } finally {
            scheduler.shutdownNow();
        }
    }
}
