package com.barbu.engine.round;

import static org.junit.jupiter.api.Assertions.*;

import com.barbu.engine.card.Card;
import com.barbu.engine.card.Rank;
import com.barbu.engine.card.Suit;
import java.util.List;
import org.junit.jupiter.api.Test;

class MontanteStateTest {
    private static Card c(Suit s, Rank r) {
        return new Card(s, r);
    }

    @Test
    void complete_when_all_but_one_finished() {
        MontanteState busy = new MontanteState(
                List.of(List.of(c(Suit.CLUBS, Rank.TWO)), List.of(), List.of(), List.of()),
                MontanteBoard.empty(),
                List.of(2),
                0,
                1);
        assertFalse(busy.isComplete());

        MontanteState done = new MontanteState(
                List.of(List.of(c(Suit.CLUBS, Rank.TWO)), List.of(), List.of(), List.of()),
                MontanteBoard.empty(),
                List.of(2, 0, 3),
                0,
                1);
        assertTrue(done.isComplete());
    }

    @Test
    void complete_on_deadlock_when_pass_streak_reaches_active_count() {
        MontanteState deadlocked = new MontanteState(
                List.of(List.of(c(Suit.CLUBS, Rank.ACE)), List.of(c(Suit.SPADES, Rank.ACE)), List.of(), List.of()),
                MontanteBoard.empty().place(c(Suit.HEARTS, Rank.EIGHT)),
                List.of(2, 3),
                2,
                0);
        assertTrue(deadlocked.isComplete());
    }

    @Test
    void final_ranking_appends_non_finishers_by_fewest_cards_left() {
        MontanteState s = new MontanteState(
                List.of(
                        List.of(c(Suit.CLUBS, Rank.ACE), c(Suit.SPADES, Rank.ACE)),
                        List.of(c(Suit.CLUBS, Rank.TWO)),
                        List.of(),
                        List.of()),
                MontanteBoard.empty(),
                List.of(2, 3),
                0,
                0);
        assertEquals(List.of(2, 3, 1, 0), s.finalRanking());
    }
}
