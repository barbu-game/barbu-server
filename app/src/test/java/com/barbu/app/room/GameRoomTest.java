package com.barbu.app.room;

import static org.junit.jupiter.api.Assertions.*;

import com.barbu.engine.variant.Variants;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
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
            while (!"GAME_OVER".equals(room.viewFor(0).get("phase")) && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            assertEquals("GAME_OVER", room.viewFor(0).get("phase"));
            assertNotNull(room.viewFor(0).get("standings"));
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
            assertEquals("LOBBY", room.viewFor(0).get("phase"));
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
            while (!"GAME_OVER".equals(room.viewFor(0).get("phase")) && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            assertEquals("GAME_OVER", room.viewFor(0).get("phase"));
            assertEquals("classic", ((Map<?, ?>) room.viewFor(0).get("variant")).get("id"));
        } finally {
            scheduler.shutdownNow();
        }
    }
}
