package com.barbu.rami.match;

import static com.barbu.rami.TestCards.c;
import static com.barbu.rami.TestCards.cs;
import static org.junit.jupiter.api.Assertions.*;

import com.barbu.rami.card.RamiCard;
import com.barbu.rami.round.Phase;
import com.barbu.rami.round.RamiMove.Discard;
import com.barbu.rami.round.RamiMove.Open;
import com.barbu.rami.round.RamiRound;
import com.barbu.rami.round.RamiRoundEngine;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RamiMatchEngineTest {

    private static final List<List<RamiCard>> LOSERS = List.of(cs("KS", "JK"), cs("AD", "7C"), cs("2C"));

    private static RamiRound seat0Plays(List<RamiCard> hand, boolean opened) {
        List<List<RamiCard>> hands = new ArrayList<>();
        hands.add(hand);
        hands.addAll(LOSERS);
        return RamiRoundEngine.setUp(
                0, Phase.PLAY, hands, cs("9C"), cs("KC"), List.of(), List.of(opened, true, true, true));
    }

    private static RamiMatch inRound(RamiRound round, List<Integer> totals) {
        return new RamiMatch(4, 4, 1L, 1, totals, round, null);
    }

    @Test
    void losers_pay_the_cards_left_in_hand() {
        RamiCard last = c("5S");
        RamiMatch m = RamiMatchEngine.apply(
                inRound(seat0Plays(List.of(last), true), List.of(0, 0, 0, 0)), 0, new Discard(last.id()));
        assertNull(m.round());
        assertEquals(new RoundSummary(0, false, List.of(0, 35, 18, 2)), m.lastRound());
        assertEquals(List.of(0, 35, 18, 2), m.totals());
    }

    @Test
    void rami_sec_doubles_the_losers_penalties() {
        List<RamiCard> run = cs("JH", "QH", "KH");
        List<RamiCard> set = cs("7S", "7D", "7C");
        RamiCard spare = c("2D");
        List<RamiCard> hand = new ArrayList<>(run);
        hand.addAll(set);
        hand.add(spare);
        RamiMatch m = inRound(seat0Plays(hand, false), List.of(10, 10, 10, 10));
        m = RamiMatchEngine.apply(
                m,
                0,
                new Open(List.of(
                        run.stream().map(RamiCard::id).toList(),
                        set.stream().map(RamiCard::id).toList())));
        m = RamiMatchEngine.apply(m, 0, new Discard(spare.id()));
        assertTrue(m.lastRound().ramiSec());
        assertEquals(List.of(10, 80, 46, 14), m.totals());
    }

    @Test
    void match_plays_one_round_per_dealer_then_completes() {
        RamiMatch m = RamiMatchEngine.newMatch(4, 3);
        assertEquals(4, m.totalRounds());
        assertNull(m.round());
        assertFalse(m.isComplete());
        m = RamiMatchEngine.startNextRound(m);
        assertEquals(1, m.roundNumber());
        assertEquals(0, m.dealer());
        assertEquals(1, m.round().current());
        RamiMatch inProgress = m;
        assertThrows(IllegalStateException.class, () -> RamiMatchEngine.startNextRound(inProgress));

        RamiMatch last = new RamiMatch(4, 4, 3L, 4, List.of(0, 0, 0, 0), null, null);
        assertTrue(last.isComplete());
        assertThrows(IllegalStateException.class, () -> RamiMatchEngine.startNextRound(last));
    }

    @Test
    void next_round_rotates_the_dealer() {
        RamiMatch afterFirst = new RamiMatch(5, 5, 3L, 1, List.of(0, 0, 0, 0, 0), null, null);
        RamiMatch second = RamiMatchEngine.startNextRound(afterFirst);
        assertEquals(1, second.dealer());
        assertEquals(2, second.round().current());
    }

    @Test
    void standings_rank_lowest_total_first_then_seat() {
        RamiMatch m = new RamiMatch(4, 4, 1L, 4, List.of(30, 5, 30, 12), null, null);
        assertEquals(List.of(1, 3, 0, 2), RamiMatchEngine.standings(m));
    }

    @Test
    void rejects_bad_table_sizes_and_round_counts() {
        assertThrows(IllegalArgumentException.class, () -> RamiMatchEngine.newMatch(3, 1));
        assertThrows(IllegalArgumentException.class, () -> RamiMatchEngine.newMatch(6, 1));
        assertThrows(IllegalArgumentException.class, () -> RamiMatchEngine.newMatch(4, 1, 0));
    }

    @Test
    void apply_without_round_is_rejected() {
        RamiMatch m = RamiMatchEngine.newMatch(4, 1);
        assertThrows(IllegalStateException.class, () -> RamiMatchEngine.apply(m, 0, new Discard(0)));
    }
}
