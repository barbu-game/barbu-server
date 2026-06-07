package com.barbu.engine.round;

import static org.junit.jupiter.api.Assertions.*;

import com.barbu.engine.card.Card;
import com.barbu.engine.card.Rank;
import com.barbu.engine.card.Suit;
import com.barbu.engine.model.Move;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MontanteRulesTest {
    private static Card c(Suit s, Rank r) {
        return new Card(s, r);
    }

    @Test
    void opening_move_must_be_eight_of_diamonds() {
        MontanteState s = new MontanteState(
                List.of(
                        List.of(c(Suit.DIAMONDS, Rank.EIGHT), c(Suit.SPADES, Rank.EIGHT)),
                        List.of(),
                        List.of(),
                        List.of()),
                MontanteBoard.empty(),
                List.of(),
                0,
                0);
        assertEquals(List.of(new Move.PlayCard(c(Suit.DIAMONDS, Rank.EIGHT))), MontanteRules.legalMoves(s, 0));
    }

    @Test
    void must_play_when_able_and_pass_only_otherwise() {
        MontanteBoard opened = MontanteBoard.empty().place(c(Suit.HEARTS, Rank.EIGHT));
        MontanteState canPlay = new MontanteState(
                List.of(List.of(c(Suit.HEARTS, Rank.SEVEN)), List.of(), List.of(), List.of()), opened, List.of(), 0, 0);
        assertEquals(List.of(new Move.PlayCard(c(Suit.HEARTS, Rank.SEVEN))), MontanteRules.legalMoves(canPlay, 0));

        MontanteState stuck = new MontanteState(
                List.of(List.of(c(Suit.CLUBS, Rank.TWO)), List.of(), List.of(), List.of()), opened, List.of(), 0, 0);
        assertEquals(List.of(new Move.Pass()), MontanteRules.legalMoves(stuck, 0));
    }

    @Test
    void emptying_a_hand_records_finishing_order_and_skips_finished() {
        MontanteState s = new MontanteState(
                List.of(List.of(c(Suit.HEARTS, Rank.SEVEN)), List.of(c(Suit.HEARTS, Rank.NINE)), List.of(), List.of()),
                MontanteBoard.empty().place(c(Suit.HEARTS, Rank.EIGHT)),
                List.of(),
                0,
                0);
        RoundState after = MontanteRules.applyMove(s, 0, new Move.PlayCard(c(Suit.HEARTS, Rank.SEVEN)));
        MontanteState m = (MontanteState) after;
        assertEquals(List.of(0), m.finishingOrder());
        assertEquals(1, m.currentPlayer());
        assertEquals(0, m.passStreak());
    }

    @Test
    void pass_increments_streak_and_advances() {
        MontanteState s = new MontanteState(
                List.of(List.of(c(Suit.CLUBS, Rank.TWO)), List.of(c(Suit.HEARTS, Rank.SEVEN)), List.of(), List.of()),
                MontanteBoard.empty().place(c(Suit.HEARTS, Rank.EIGHT)),
                List.of(),
                0,
                0);
        MontanteState after = (MontanteState) MontanteRules.applyMove(s, 0, new Move.Pass());
        assertEquals(1, after.currentPlayer());
        assertEquals(1, after.passStreak());
    }

    @Test
    void illegal_pass_when_a_card_is_playable_is_rejected() {
        MontanteState s = new MontanteState(
                List.of(List.of(c(Suit.HEARTS, Rank.SEVEN)), List.of(), List.of(), List.of()),
                MontanteBoard.empty().place(c(Suit.HEARTS, Rank.EIGHT)),
                List.of(),
                0,
                0);
        assertThrows(IllegalArgumentException.class, () -> MontanteRules.applyMove(s, 0, new Move.Pass()));
    }

    @Test
    void score_follows_ranking_for_n_players() {
        MontanteState four = new MontanteState(
                List.of(List.of(c(Suit.CLUBS, Rank.TWO)), List.of(), List.of(), List.of()),
                MontanteBoard.empty(),
                List.of(2, 0, 3),
                0,
                1);
        Map<Integer, Integer> s4 = MontanteRules.score(four);
        assertEquals(15, s4.get(2));
        assertEquals(5, s4.get(0));
        assertEquals(-5, s4.get(3));
        assertEquals(-15, s4.get(1));

        MontanteState three = new MontanteState(
                List.of(List.of(), List.of(c(Suit.CLUBS, Rank.TWO)), List.of()),
                MontanteBoard.empty(),
                List.of(0, 2),
                0,
                1);
        Map<Integer, Integer> s3 = MontanteRules.score(three);
        assertEquals(10, s3.get(0));
        assertEquals(0, s3.get(2));
        assertEquals(-10, s3.get(1));
    }
}
