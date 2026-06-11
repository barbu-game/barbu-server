package com.barbu.engine.round;

import static org.junit.jupiter.api.Assertions.*;

import com.barbu.engine.card.Card;
import com.barbu.engine.card.Rank;
import com.barbu.engine.card.Suit;
import com.barbu.engine.model.Contract;
import com.barbu.engine.model.Move;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrickTakingRulesTest {
    private static Card c(Suit s, Rank r) {
        return new Card(s, r);
    }

    @Test
    void must_follow_led_suit_when_able() {
        List<List<Card>> hands = List.of(
                List.of(c(Suit.HEARTS, Rank.TWO)),
                List.of(c(Suit.HEARTS, Rank.KING), c(Suit.SPADES, Rank.ACE)),
                List.of(),
                List.of());
        TrickTakingState s = new TrickTakingState(
                Contract.NO_TRICKS,
                hands,
                Trick.startedBy(0, 4).withCard(c(Suit.HEARTS, Rank.TWO)),
                noCapture(4),
                List.of(),
                1);

        assertEquals(List.of(new Move.PlayCard(c(Suit.HEARTS, Rank.KING))), TrickTakingRules.legalMoves(s, 1));
    }

    @Test
    void may_play_anything_when_void_in_led_suit() {
        List<List<Card>> hands = List.of(
                List.of(c(Suit.HEARTS, Rank.TWO)),
                List.of(c(Suit.SPADES, Rank.ACE), c(Suit.CLUBS, Rank.KING)),
                List.of(),
                List.of());
        TrickTakingState s = new TrickTakingState(
                Contract.NO_TRICKS,
                hands,
                Trick.startedBy(0, 4).withCard(c(Suit.HEARTS, Rank.TWO)),
                noCapture(4),
                List.of(),
                1);

        assertEquals(2, TrickTakingRules.legalMoves(s, 1).size());
    }

    @Test
    void completing_a_trick_gives_capture_and_lead_to_winner() {
        List<List<Card>> hands = List.of(
                List.of(c(Suit.HEARTS, Rank.TWO)),
                List.of(c(Suit.HEARTS, Rank.KING)),
                List.of(c(Suit.HEARTS, Rank.NINE)),
                List.of(c(Suit.HEARTS, Rank.FOUR)));
        RoundState s =
                new TrickTakingState(Contract.NO_TRICKS, hands, Trick.startedBy(0, 4), noCapture(4), List.of(), 0);

        s = TrickTakingRules.applyMove((TrickTakingState) s, 0, play(Suit.HEARTS, Rank.TWO));
        s = TrickTakingRules.applyMove((TrickTakingState) s, 1, play(Suit.HEARTS, Rank.KING));
        s = TrickTakingRules.applyMove((TrickTakingState) s, 2, play(Suit.HEARTS, Rank.NINE));
        s = TrickTakingRules.applyMove((TrickTakingState) s, 3, play(Suit.HEARTS, Rank.FOUR));

        TrickTakingState done = (TrickTakingState) s;
        assertTrue(done.isComplete());
        assertEquals(1, done.currentPlayer());
        assertEquals(4, done.captured().get(1).size());
    }

    @Test
    void two_player_trick_completes_after_two_cards() {
        List<List<Card>> hands = List.of(List.of(c(Suit.HEARTS, Rank.TWO)), List.of(c(Suit.HEARTS, Rank.KING)));
        RoundState s =
                new TrickTakingState(Contract.NO_TRICKS, hands, Trick.startedBy(0, 2), noCapture(2), List.of(), 0);
        s = TrickTakingRules.applyMove((TrickTakingState) s, 0, play(Suit.HEARTS, Rank.TWO));
        s = TrickTakingRules.applyMove((TrickTakingState) s, 1, play(Suit.HEARTS, Rank.KING));
        assertTrue(s.isComplete());
        assertEquals(1, ((TrickTakingState) s).currentPlayer());
    }

    @Test
    void illegal_move_and_wrong_player_are_rejected() {
        List<List<Card>> hands = List.of(
                List.of(c(Suit.HEARTS, Rank.TWO)),
                List.of(c(Suit.HEARTS, Rank.KING), c(Suit.SPADES, Rank.ACE)),
                List.of(),
                List.of());
        TrickTakingState s = new TrickTakingState(
                Contract.NO_TRICKS,
                hands,
                Trick.startedBy(0, 4).withCard(c(Suit.HEARTS, Rank.TWO)),
                noCapture(4),
                List.of(),
                1);

        assertThrows(
                IllegalArgumentException.class, () -> TrickTakingRules.applyMove(s, 1, play(Suit.SPADES, Rank.ACE)));
        assertThrows(
                IllegalArgumentException.class, () -> TrickTakingRules.applyMove(s, 2, play(Suit.HEARTS, Rank.KING)));
    }

    private static Move play(Suit s, Rank r) {
        return new Move.PlayCard(new Card(s, r));
    }

    private static List<List<Card>> noCapture(int n) {
        List<List<Card>> out = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) out.add(List.of());
        return out;
    }
}
