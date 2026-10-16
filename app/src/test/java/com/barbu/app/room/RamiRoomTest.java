package com.barbu.app.room;

import static org.junit.jupiter.api.Assertions.*;

import com.barbu.app.protocol.GameStateMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RamiRoomTest {

    private static GameRoom ramiRoom(InlineScheduler scheduler, int players) {
        return new GameRoom(
                "RAMI1",
                players,
                new RamiSession(players, null),
                new ObjectMapper(),
                scheduler,
                0,
                null,
                null,
                "private",
                null,
                null,
                60_000,
                2);
    }

    private static void drain(InlineScheduler scheduler, GameRoom room, int maxSteps) {
        for (int i = 0; i < maxSteps && !"GAME_OVER".equals(room.viewFor(0).phase()); i++) {
            if (!scheduler.step()) {
                return;
            }
        }
    }

    @Test
    void all_bot_rami_table_plays_to_completion() {
        InlineScheduler scheduler = new InlineScheduler();
        GameRoom room = ramiRoom(scheduler, 5);
        for (int i = 0; i < 5; i++) {
            assertTrue(room.addBot());
        }
        assertTrue(room.start(42L));
        drain(scheduler, room, 2_000_000);
        GameStateMessage end = room.viewFor(0);
        assertEquals("GAME_OVER", end.phase());
        assertEquals("RAMI", end.gameType());
        assertEquals("rami51", end.variant().id());
        assertEquals(5, end.standings().size());
        assertNotNull(end.rami().lastRound());
    }

    /** Seat 0 is the human; seats 1..3 are bots. Runs the table until the human must act. */
    private static GameRoom untilHumanTurn(InlineScheduler scheduler, FakeSession human) {
        GameRoom room = ramiRoom(scheduler, 4);
        assertEquals(0, room.addHuman(human, "Alice", null));
        for (int i = 0; i < 3; i++) {
            room.addBot();
        }
        assertTrue(room.start(11L));
        for (int i = 0; i < 100_000; i++) {
            GameStateMessage v = room.viewFor(0);
            if (Integer.valueOf(0).equals(v.currentActor()) && "PLAYING".equals(v.phase())) {
                return room;
            }
            assertTrue(scheduler.step(), "table stalled before the human's turn");
        }
        throw new AssertionError("human never got a turn");
    }

    @Test
    void human_draws_then_an_illegal_discard_throws_and_keeps_the_turn() {
        InlineScheduler scheduler = new InlineScheduler();
        FakeSession human = new FakeSession();
        GameRoom room = untilHumanTurn(scheduler, human);
        assertEquals("DRAW", room.viewFor(0).rami().turnPhase());

        room.play(0, Map.of("kind", "draw", "source", "DISCARD"));
        GameStateMessage afterDraw = room.viewFor(0);
        assertEquals("PLAY", afterDraw.rami().turnPhase());
        assertEquals(List.of(), afterDraw.rami().mustPlay(), "a taken discard may stay in hand");

        assertThrows(IllegalArgumentException.class, () -> room.play(0, Map.of("kind", "discard", "cardId", 999)));
        assertThrows(IllegalArgumentException.class, () -> room.play(0, Map.of("kind", "discard", "cardId", "x")));
        assertEquals(0, room.viewFor(0).currentActor());
        assertNotNull(room.viewFor(0).turnDeadlineEpochMs(), "the turn timer survives a rejected move");
    }

    @Test
    void timeout_after_taking_the_discard_plays_one_whole_turn() {
        InlineScheduler scheduler = new InlineScheduler();
        FakeSession human = new FakeSession();
        GameRoom room = untilHumanTurn(scheduler, human);
        room.play(0, Map.of("kind", "draw", "source", "DISCARD"));
        int handBefore = room.viewFor(0).rami().yourHand().size();

        // The next queued task is the re-armed turn timer for seat 0.
        assertTrue(scheduler.step());
        GameStateMessage v = room.viewFor(0);
        assertNotEquals(0, v.currentActor());
        assertEquals(handBefore - 1, v.rami().yourHand().size());
        assertFalse(v.players().get(0).bot(), "a single timeout is one strike, not an eviction");
    }

    @Test
    void sub_moves_do_not_restart_the_turn_timer() throws Exception {
        InlineScheduler scheduler = new InlineScheduler();
        GameRoom room = untilHumanTurn(scheduler, new FakeSession());
        room.play(0, Map.of("kind", "draw", "source", "STOCK"));
        Long deadline = room.viewFor(0).turnDeadlineEpochMs();
        assertNotNull(deadline);
        for (int i = 0; i < 3; i++) {
            Thread.sleep(5);
            room.play(0, Map.of("kind", "reset"));
            assertEquals(deadline, room.viewFor(0).turnDeadlineEpochMs(), "a reset must not buy more time");
        }
        // The single pending task is the original timer: when it fires, the turn passes.
        assertTrue(scheduler.step());
        assertNotEquals(0, room.viewFor(0).currentActor());
    }
}
