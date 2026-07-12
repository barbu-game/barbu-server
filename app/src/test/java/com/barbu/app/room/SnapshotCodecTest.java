package com.barbu.app.room;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.barbu.engine.match.MatchEngine;
import com.barbu.engine.match.MatchState;
import com.barbu.engine.model.Contract;
import com.barbu.engine.round.MontanteBoard;
import com.barbu.engine.round.MontanteState;
import com.barbu.engine.variant.Variants;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SnapshotCodecTest {

    private final SnapshotCodec codec = new SnapshotCodec(new ObjectMapper());

    @Test
    void round_trips_a_started_match_trick_state() {
        MatchState match = MatchEngine.newMatch(5, 42L, Variants.DEVELOPER);
        GameSnapshot snap = new GameSnapshot(
                "ABCDE",
                5,
                Variants.DEVELOPER.id(),
                "casual",
                new String[] {"Alice", "Bob", "Bot 1", "Bot 2", "Bot 3"},
                new boolean[] {false, false, true, true, true},
                new Long[] {7L, null, null, null, null},
                new String[] {"tok-a", "tok-b", null, null, null},
                false,
                match);

        String json = codec.encode(snap);
        GameSnapshot back = codec.decode(json);

        // JSON stability: decode∘encode is a faithful inverse (records with int[] fields make
        // .equals() reference-based, so we compare the canonical serialized form instead).
        assertEquals(json, codec.encode(back));
        assertEquals(snap.roomId(), back.roomId());
        assertEquals(snap.playerCount(), back.playerCount());
        assertEquals(snap.variantId(), back.variantId());
        assertArrayEquals(snap.names(), back.names());
        assertArrayEquals(snap.isBot(), back.isBot());
        assertArrayEquals(snap.userIds(), back.userIds());
        assertArrayEquals(snap.resumeTokens(), back.resumeTokens());
        assertEquals(match.seed(), back.match().seed());
        // newMatch leaves round null until the first contract step is dealt; it round-trips as null.
        assertNull(back.match().round());
    }

    @Test
    void round_trips_a_montante_round_state() {
        MontanteState montante = new MontanteState(
                List.of(List.of(), List.of(), List.of(), List.of(), List.of()),
                new MontanteBoard(new int[4], new int[4]),
                List.of(),
                0,
                0);
        MatchState match = new MatchState(
                5, 1L, 0, 4, 5, Set.of(Contract.NO_TRICKS), montante, new int[5], List.of(), Variants.DEVELOPER);
        GameSnapshot snap = new GameSnapshot(
                "MNTNT",
                5,
                Variants.DEVELOPER.id(),
                "casual",
                new String[] {"a", "b", "c", "d", "e"},
                new boolean[5],
                new Long[5],
                new String[] {"1", "2", "3", "4", "5"},
                false,
                match);

        String json = codec.encode(snap);

        assertEquals(json, codec.encode(codec.decode(json)));
        assertEquals(Contract.MONTANTE, codec.decode(json).match().round().contract());
    }
}
