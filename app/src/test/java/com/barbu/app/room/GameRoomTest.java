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

    @Test
    void round_scores_are_exposed_during_the_montante() {
        // An inline scheduler drives the all-bot game one action at a time (delay 0 → no pauses), so the
        // brief montante window is observed deterministically instead of being raced against wall-clock.
        InlineScheduler scheduler = new InlineScheduler();
        try {
            GameRoom room = new GameRoom("MNTSC", 3, Variants.DEVELOPER, new ObjectMapper(), scheduler, 0, null, null);
            assertTrue(room.addBot());
            assertTrue(room.addBot());
            assertTrue(room.addBot());
            assertTrue(room.start(42L));

            GameStateMessage montante = null;
            int guard = 0;
            while (scheduler.step() && guard++ < 200_000) {
                GameStateMessage v = room.viewFor(0);
                if ("MONTANTE".equals(v.contract())) {
                    montante = v;
                    break;
                }
            }
            assertNotNull(montante, "expected to observe the montante in progress");
            assertNotNull(montante.roundScores(), "roundScores must be exposed during the montante");
            assertEquals(3, montante.roundScores().size(), "roundScores is indexed by seat");
            for (int p : montante.roundScores()) {
                assertTrue(p >= -30 && p <= 30, "montante running score out of range: " + p);
            }
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void bots_are_numbered_by_count_not_by_seat() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            GameRoom room = new GameRoom("HOSTB", 5, Variants.DEVELOPER, new ObjectMapper(), scheduler, 0, null, null);
            room.addHuman(new FakeSession(), "Alice", null); // seat 0
            room.addHuman(new FakeSession(), "Bob", null); // seat 1
            assertTrue(room.addBot()); // seat 2
            assertTrue(room.addBot()); // seat 3
            assertEquals("Bot 1", room.viewFor(0).players().get(2).name());
            assertEquals("Bot 2", room.viewFor(0).players().get(3).name());
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void host_is_lowest_seated_connected_human() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            GameRoom room = new GameRoom("HOSTH", 4, Variants.DEVELOPER, new ObjectMapper(), scheduler, 0, null, null);
            assertEquals(-1, room.hostSeat());
            room.addHuman(new FakeSession(), "Alice", null); // seat 0
            room.addHuman(new FakeSession(), "Bob", null); // seat 1
            room.addBot(); // seat 2
            assertEquals(0, room.hostSeat());
            assertTrue(room.isHost(0));
            assertFalse(room.isHost(1));
            assertFalse(room.isHost(2));
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void host_can_rename_a_bot_within_the_name_limit() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            GameRoom room = new GameRoom("RENB", 3, Variants.DEVELOPER, new ObjectMapper(), scheduler, 0, null, null);
            room.addHuman(new FakeSession(), "Alice", null); // seat 0
            room.addBot(); // seat 1, "Bot 1"
            assertTrue(room.renameBot(1, "  Rex  "));
            assertEquals("Rex", room.viewFor(0).players().get(1).name());
            assertTrue(room.renameBot(1, "z".repeat(60)));
            assertEquals("z".repeat(40), room.viewFor(0).players().get(1).name());
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void renameBot_rejects_non_bot_and_blank() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            GameRoom room = new GameRoom("RENX", 3, Variants.DEVELOPER, new ObjectMapper(), scheduler, 0, null, null);
            room.addHuman(new FakeSession(), "Alice", null); // seat 0 (human)
            room.addBot(); // seat 1
            assertFalse(room.renameBot(0, "Nope")); // not a bot
            assertFalse(room.renameBot(1, "   ")); // blank
            assertEquals("Bot 1", room.viewFor(0).players().get(1).name());
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void leaving_frees_the_seat_and_migrates_the_host() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            GameRoom room = new GameRoom("LEAVE", 4, Variants.DEVELOPER, new ObjectMapper(), scheduler, 0, null, null);
            room.addHuman(new FakeSession(), "Alice", null); // seat 0 (host)
            room.addHuman(new FakeSession(), "Bob", null); // seat 1
            room.addBot(); // seat 2
            assertEquals(0, room.hostSeat());
            assertTrue(room.leave(0));
            assertEquals(1, room.hostSeat()); // migrated
            assertFalse(room.isEmptyOfHumans());
            assertTrue(room.leave(1));
            assertTrue(room.isEmptyOfHumans());
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void leave_is_refused_on_empty_seat() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            GameRoom room = new GameRoom("LEAV2", 3, Variants.DEVELOPER, new ObjectMapper(), scheduler, 0, null, null);
            room.addHuman(new FakeSession(), "Alice", null); // seat 0
            assertFalse(room.leave(1)); // nobody there
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void a_disconnected_seat_is_reserved_for_reclaim_not_handed_to_a_joiner() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            GameRoom room = new GameRoom("RSV", 3, Variants.DEVELOPER, new ObjectMapper(), scheduler, 0, null, null);
            int seatA = room.addHuman(new FakeSession(), "Alice", 1L); // seat 0
            assertEquals(0, seatA);
            room.handleDisconnect(seatA); // Alice backgrounds: seat 0 stays reserved
            int seatB = room.addHuman(new FakeSession(), "Bob", 2L); // must skip the reserved seat 0
            assertEquals(1, seatB);
            int reclaimed = room.reclaim(new FakeSession(), 1L, null); // Alice returns
            assertEquals(0, reclaimed);
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void grace_teardown_runs_only_while_the_room_stays_empty_of_humans() throws InterruptedException {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            GameRoom occupied =
                    new GameRoom("GRC1", 3, Variants.DEVELOPER, new ObjectMapper(), scheduler, 0, null, null);
            occupied.addHuman(new FakeSession(), "Alice", null); // still connected
            boolean[] tornWhileOccupied = {false};
            occupied.scheduleTeardown(20, () -> tornWhileOccupied[0] = true);
            Thread.sleep(80);
            assertFalse(tornWhileOccupied[0], "must not tear down a room with a connected human");

            GameRoom empty = new GameRoom("GRC2", 3, Variants.DEVELOPER, new ObjectMapper(), scheduler, 0, null, null);
            int seat = empty.addHuman(new FakeSession(), "Alice", null);
            empty.handleDisconnect(seat); // now empty of humans
            boolean[] tornWhenEmpty = {false};
            empty.scheduleTeardown(20, () -> tornWhenEmpty[0] = true);
            Thread.sleep(80);
            assertTrue(tornWhenEmpty[0], "must tear down a room that stays empty through the grace window");
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void open_seat_has_no_name_while_a_disconnected_seat_keeps_its_name() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            GameRoom room = new GameRoom("VIEWN", 3, Variants.DEVELOPER, new ObjectMapper(), scheduler, 0, null, null);
            int seat = room.addHuman(new FakeSession(), "Alice", null); // seat 0
            assertEquals("", room.viewFor(0).players().get(1).name(), "an open seat carries no name");
            room.handleDisconnect(seat); // Alice backgrounds: seat reserved, name preserved
            assertEquals("Alice", room.viewFor(0).players().get(0).name());
            assertFalse(room.viewFor(0).players().get(0).connected());
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void reconnecting_cancels_the_pending_grace_teardown() throws InterruptedException {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            GameRoom room = new GameRoom("GRC3", 3, Variants.DEVELOPER, new ObjectMapper(), scheduler, 0, null, null);
            int seat = room.addHuman(new FakeSession(), "Alice", 1L);
            room.handleDisconnect(seat);
            boolean[] torn = {false};
            room.scheduleTeardown(60, () -> torn[0] = true);
            room.reclaim(new FakeSession(), 1L, null); // Alice returns before the window closes
            Thread.sleep(120);
            assertFalse(torn[0], "a reclaim within the grace window cancels the teardown");
        } finally {
            scheduler.shutdownNow();
        }
    }
}
