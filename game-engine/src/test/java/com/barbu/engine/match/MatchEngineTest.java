package com.barbu.engine.match;

import static org.junit.jupiter.api.Assertions.*;

import com.barbu.engine.model.Contract;
import com.barbu.engine.round.RoundState;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MatchEngineTest {
    @Test
    void new_match_default_length_is_five_times_players() {
        MatchState m = MatchEngine.newMatch(4, 123L);
        assertEquals(20, m.plannedRounds());
        assertEquals(0, m.dealer());
        assertNull(m.round());
        assertFalse(MatchEngine.isComplete(m));
        assertEquals(4, m.totals().length);
    }

    @Test
    void choosing_a_contract_deals_equal_hands() {
        MatchState m = MatchEngine.chooseContract(MatchEngine.newMatch(5, 9L), Contract.NO_TRICKS);
        RoundState round = m.round();
        assertNotNull(round);
        assertEquals(5, round.playerCount());
        assertEquals(1, round.currentPlayer());
    }

    @Test
    void dealer_cannot_replay_a_contract_in_their_turn() {
        MatchState m = MatchEngine.playOut(MatchEngine.chooseContract(MatchEngine.newMatch(3, 1L), Contract.NO_TRICKS));
        assertThrows(IllegalArgumentException.class, () -> MatchEngine.chooseContract(m, Contract.NO_TRICKS));
    }

    @Test
    void contracts_are_imposed_in_a_fixed_order_per_dealer() {
        MatchState m = MatchEngine.newMatch(3, 1L);
        for (Contract expected : Contract.values()) {
            assertEquals(expected, MatchEngine.nextContract(m));
            m = MatchEngine.playOut(MatchEngine.startNextContract(m));
        }
        // dealer has rotated; the imposed sequence restarts at the first contract
        assertEquals(Contract.values()[0], MatchEngine.nextContract(m));
    }

    @Test
    void dealer_rotates_after_five_contracts_and_marks_boundary() {
        MatchState m = MatchEngine.newMatch(3, 1L);
        for (Contract contract : Contract.values()) {
            m = MatchEngine.playOut(MatchEngine.chooseContract(m, contract));
        }
        assertEquals(1, m.dealer());
        assertEquals(5, m.roundNumber());
        assertTrue(MatchEngine.isDealerBoundary(m));
    }

    @Test
    void honours_a_shorter_planned_length() {
        MatchState m = MatchEngine.newMatch(4, 7L, 5);
        int rounds = 0;
        while (!MatchEngine.isComplete(m)) {
            m = MatchEngine.playOut(MatchEngine.chooseContract(m, nextUnplayed(m)));
            rounds++;
        }
        assertEquals(5, rounds);
    }

    @Test
    void a_full_match_completes_and_ranks_everyone() {
        for (int n = 2; n <= 6; n++) {
            MatchState m = MatchEngine.newMatch(n, 2024L);
            while (!MatchEngine.isComplete(m)) {
                m = MatchEngine.playOut(MatchEngine.chooseContract(m, nextUnplayed(m)));
            }
            assertEquals(5 * n, m.roundNumber(), "n=" + n);
            List<Integer> standings = MatchEngine.standings(m);
            Set<Integer> expected = new HashSet<>();
            for (int s = 0; s < n; s++) expected.add(s);
            assertEquals(expected, new HashSet<>(standings), "n=" + n);
        }
    }

    private static Contract nextUnplayed(MatchState m) {
        for (Contract contract : Contract.values()) {
            if (!m.playedByDealer().contains(contract)) {
                return contract;
            }
        }
        throw new IllegalStateException("all contracts played");
    }
}
