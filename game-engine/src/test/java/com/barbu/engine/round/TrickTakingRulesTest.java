package com.barbu.engine.round;

import com.barbu.engine.card.Card;
import com.barbu.engine.card.Rank;
import com.barbu.engine.card.Suit;
import com.barbu.engine.model.Contract;
import com.barbu.engine.model.Move;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TrickTakingRulesTest {
    private static Card c(Suit s, Rank r) {
        return new Card(s, r);
    }

    @Test
    void must_follow_led_suit_when_able() {
        List<List<Card>> hands = List.of(
                List.of(c(Suit.HEARTS, Rank.TWO)),
                List.of(c(Suit.HEARTS, Rank.KING), c(Suit.SPADES, Rank.ACE)),
                List.of(), List.of());
        TrickTakingState s = new TrickTakingState(
                Contract.NO_TRICKS, hands,
                Trick.startedBy(0, 4).withCard(c(Suit.HEARTS, Rank.TWO)),
                noCapture(4), 1);

        assertEquals(List.of(new Move.PlayCard(c(Suit.HEARTS, Rank.KING))),
                TrickTakingRules.legalMoves(s, 1));
    }

    @Test
    void may_play_anything_when_void_in_led_suit() {
        List<List<Card>> hands = List.of(
                List.of(c(Suit.HEARTS, Rank.TWO)),
                List.of(c(Suit.SPADES, Rank.ACE), c(Suit.CLUBS, Rank.KING)),
                List.of(), List.of());
        TrickTakingState s = new TrickTakingState(
                Contract.NO_TRICKS, hands,
                Trick.startedBy(0, 4).withCard(c(Suit.HEARTS, Rank.TWO)),
                noCapture(4), 1);

        assertEquals(2, TrickTakingRules.legalMoves(s, 1).size());
    }

    @Test
    void completing_a_trick_gives_capture_and_lead_to_winner() {
        List<List<Card>> hands = List.of(
                List.of(c(Suit.HEARTS, Rank.TWO)),
                List.of(c(Suit.HEARTS, Rank.KING)),
                List.of(c(Suit.HEARTS, Rank.NINE)),
                List.of(c(Suit.HEARTS, Rank.FOUR)));
        RoundState s = new TrickTakingState(Contract.NO_TRICKS, hands, Trick.startedBy(0, 4), noCapture(4), 0);

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
        List<List<Card>> hands = List.of(
                List.of(c(Suit.HEARTS, Rank.TWO)),
                List.of(c(Suit.HEARTS, Rank.KING)));
        RoundState s = new TrickTakingState(Contract.NO_TRICKS, hands, Trick.startedBy(0, 2), noCapture(2), 0);
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
                List.of(), List.of());
        TrickTakingState s = new TrickTakingState(
                Contract.NO_TRICKS, hands,
                Trick.startedBy(0, 4).withCard(c(Suit.HEARTS, Rank.TWO)),
                noCapture(4), 1);

        assertThrows(IllegalArgumentException.class, () -> TrickTakingRules.applyMove(s, 1, play(Suit.SPADES, Rank.ACE)));
        assertThrows(IllegalArgumentException.class, () -> TrickTakingRules.applyMove(s, 2, play(Suit.HEARTS, Rank.KING)));
    }

    @Test
    void score_no_tricks_counts_tricks_as_captured_over_player_count() {
        List<List<Card>> captured = List.of(List.of(), List.of(), eightCards(), List.of());
        TrickTakingState s = completed(Contract.NO_TRICKS, captured);
        assertEquals(-4, TrickTakingRules.score(s).get(2));
        assertEquals(0, TrickTakingRules.score(s).get(0));
    }

    @Test
    void score_hearts_queens_red_kings() {
        List<List<Card>> hearts = List.of(
                List.of(c(Suit.HEARTS, Rank.TWO), c(Suit.HEARTS, Rank.KING), c(Suit.SPADES, Rank.ACE)),
                List.of(), List.of(), List.of());
        assertEquals(-4, TrickTakingRules.score(completed(Contract.NO_HEARTS, hearts)).get(0));

        List<List<Card>> kings = List.of(
                List.of(c(Suit.HEARTS, Rank.KING), c(Suit.DIAMONDS, Rank.KING), c(Suit.SPADES, Rank.KING)),
                List.of(), List.of(), List.of());
        assertEquals(-20, TrickTakingRules.score(completed(Contract.NO_RED_KINGS, kings)).get(0));

        List<List<Card>> queens = List.of(
                List.of(c(Suit.SPADES, Rank.QUEEN), c(Suit.HEARTS, Rank.QUEEN)),
                List.of(), List.of(), List.of());
        assertEquals(-12, TrickTakingRules.score(completed(Contract.NO_QUEENS, queens)).get(0));
    }

    private static Move play(Suit s, Rank r) {
        return new Move.PlayCard(new Card(s, r));
    }

    private static List<List<Card>> noCapture(int n) {
        List<List<Card>> out = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) out.add(List.of());
        return out;
    }

    private static List<Card> eightCards() {
        return List.of(
                new Card(Suit.SPADES, Rank.TWO), new Card(Suit.SPADES, Rank.THREE),
                new Card(Suit.SPADES, Rank.FOUR), new Card(Suit.SPADES, Rank.FIVE),
                new Card(Suit.CLUBS, Rank.TWO), new Card(Suit.CLUBS, Rank.THREE),
                new Card(Suit.CLUBS, Rank.FOUR), new Card(Suit.CLUBS, Rank.FIVE));
    }

    private static TrickTakingState completed(Contract contract, List<List<Card>> captured) {
        return new TrickTakingState(contract,
                List.of(List.of(), List.of(), List.of(), List.of()),
                Trick.startedBy(0, 4), captured, 0);
    }
}
