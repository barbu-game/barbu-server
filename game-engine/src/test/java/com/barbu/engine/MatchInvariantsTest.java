package com.barbu.engine;

import static org.junit.jupiter.api.Assertions.*;

import com.barbu.engine.match.MatchEngine;
import com.barbu.engine.match.MatchState;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;

class MatchInvariantsTest {

    private static MatchState playFull(int n, long seed) {
        MatchState m = MatchEngine.newMatch(n, seed);
        while (!MatchEngine.isComplete(m)) {
            m = MatchEngine.playOut(MatchEngine.startNextContract(m));
        }
        return m;
    }

    @Property
    void any_player_count_and_seed_completes_five_times_n_rounds(
            @ForAll @IntRange(min = 2, max = 10) int n, @ForAll @LongRange(min = 0, max = 120) long seed) {
        MatchState m = playFull(n, seed);
        assertEquals(5 * n, m.roundNumber(), "n=" + n + " seed=" + seed);
        assertEquals(5 * n, m.history().size());
    }

    @Property
    void same_inputs_are_fully_reproducible(
            @ForAll @IntRange(min = 2, max = 10) int n, @ForAll @LongRange(min = 0, max = 120) long seed) {
        assertArrayEquals(playFull(n, seed).totals(), playFull(n, seed).totals(), "n=" + n + " seed=" + seed);
    }
}
