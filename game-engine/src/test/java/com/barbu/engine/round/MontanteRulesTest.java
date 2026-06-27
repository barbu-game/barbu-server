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

    /** Open {@code suit} all the way up to the King, so the Ace becomes playable on its high end. */
    private static MontanteBoard openedToKing(Suit suit) {
        MontanteBoard b = MontanteBoard.empty();
        for (Rank r : new Rank[] {Rank.EIGHT, Rank.NINE, Rank.TEN, Rank.JACK, Rank.QUEEN, Rank.KING}) {
            b = b.place(c(suit, r));
        }
        return b;
    }

    @Test
    void playing_an_ace_grants_a_replay_to_the_same_seat() {
        MontanteState s = new MontanteState(
                List.of(List.of(c(Suit.HEARTS, Rank.ACE), c(Suit.HEARTS, Rank.SEVEN)), List.of(), List.of(), List.of()),
                openedToKing(Suit.HEARTS),
                List.of(),
                0,
                0);
        MontanteState after =
                (MontanteState) MontanteRules.applyMove(s, 0, new Move.PlayCard(c(Suit.HEARTS, Rank.ACE)));
        assertEquals(0, after.currentPlayer());
        assertEquals(List.of(), after.finishingOrder());
        assertEquals(0, after.passStreak());
    }

    @Test
    void ace_replay_chains_through_further_aces_until_the_hand_empties() {
        MontanteState s = new MontanteState(
                List.of(
                        List.of(c(Suit.HEARTS, Rank.ACE), c(Suit.SPADES, Rank.ACE), c(Suit.HEARTS, Rank.SEVEN)),
                        List.of(c(Suit.CLUBS, Rank.TWO)),
                        List.of(),
                        List.of()),
                openedToKing(Suit.HEARTS)
                        .place(c(Suit.SPADES, Rank.EIGHT))
                        .place(c(Suit.SPADES, Rank.NINE))
                        .place(c(Suit.SPADES, Rank.TEN))
                        .place(c(Suit.SPADES, Rank.JACK))
                        .place(c(Suit.SPADES, Rank.QUEEN))
                        .place(c(Suit.SPADES, Rank.KING)),
                List.of(),
                0,
                0);
        MontanteState a1 = (MontanteState) MontanteRules.applyMove(s, 0, new Move.PlayCard(c(Suit.HEARTS, Rank.ACE)));
        assertEquals(0, a1.currentPlayer(), "first Ace keeps the turn");
        MontanteState a2 = (MontanteState) MontanteRules.applyMove(a1, 0, new Move.PlayCard(c(Suit.SPADES, Rank.ACE)));
        assertEquals(0, a2.currentPlayer(), "second Ace keeps the turn again");
        MontanteState a3 =
                (MontanteState) MontanteRules.applyMove(a2, 0, new Move.PlayCard(c(Suit.HEARTS, Rank.SEVEN)));
        assertEquals(1, a3.currentPlayer(), "emptying the hand on the last card hands over");
    }

    @Test
    void ace_free_play_continues_across_normal_cards_until_a_pass() {
        MontanteState s = new MontanteState(
                List.of(
                        List.of(c(Suit.HEARTS, Rank.ACE), c(Suit.HEARTS, Rank.SEVEN), c(Suit.HEARTS, Rank.SIX)),
                        List.of(c(Suit.CLUBS, Rank.TWO)),
                        List.of(),
                        List.of()),
                openedToKing(Suit.HEARTS),
                List.of(),
                0,
                0);
        MontanteState afterAce =
                (MontanteState) MontanteRules.applyMove(s, 0, new Move.PlayCard(c(Suit.HEARTS, Rank.ACE)));
        MontanteState afterSeven =
                (MontanteState) MontanteRules.applyMove(afterAce, 0, new Move.PlayCard(c(Suit.HEARTS, Rank.SEVEN)));
        assertEquals(0, afterSeven.currentPlayer(), "a normal card keeps the ace free-play turn");

        List<Move> legal = MontanteRules.legalMoves(afterSeven, 0);
        assertTrue(legal.contains(new Move.PlayCard(c(Suit.HEARTS, Rank.SIX))), "the next card stays playable");
        assertTrue(legal.contains(new Move.Pass()), "the player may stop by passing");

        MontanteState afterPass = (MontanteState) MontanteRules.applyMove(afterSeven, 0, new Move.Pass());
        assertEquals(1, afterPass.currentPlayer(), "passing ends the free-play and hands over");
        assertEquals(0, afterPass.passStreak(), "ending the free-play is not a blocking pass");
    }

    @Test
    void ace_free_play_ends_on_its_own_when_no_playable_card_remains() {
        MontanteState s = new MontanteState(
                List.of(
                        List.of(c(Suit.HEARTS, Rank.ACE), c(Suit.HEARTS, Rank.SEVEN), c(Suit.CLUBS, Rank.TWO)),
                        List.of(c(Suit.SPADES, Rank.THREE)),
                        List.of(),
                        List.of()),
                openedToKing(Suit.HEARTS),
                List.of(),
                0,
                0);
        MontanteState afterAce =
                (MontanteState) MontanteRules.applyMove(s, 0, new Move.PlayCard(c(Suit.HEARTS, Rank.ACE)));
        MontanteState afterSeven =
                (MontanteState) MontanteRules.applyMove(afterAce, 0, new Move.PlayCard(c(Suit.HEARTS, Rank.SEVEN)));
        assertEquals(1, afterSeven.currentPlayer(), "with no playable card left, the free-play turn ends");
        assertEquals(0, afterSeven.passStreak(), "running out of plays is not a blocking pass");
    }

    @Test
    void ace_follow_up_lets_the_player_pass_instead_of_replaying() {
        MontanteState s = new MontanteState(
                List.of(
                        List.of(c(Suit.HEARTS, Rank.ACE), c(Suit.HEARTS, Rank.SEVEN)),
                        List.of(c(Suit.CLUBS, Rank.TWO)),
                        List.of(),
                        List.of()),
                openedToKing(Suit.HEARTS),
                List.of(),
                0,
                0);
        MontanteState afterAce =
                (MontanteState) MontanteRules.applyMove(s, 0, new Move.PlayCard(c(Suit.HEARTS, Rank.ACE)));

        assertEquals(0, afterAce.currentPlayer(), "the Ace keeps the turn");
        List<Move> legal = MontanteRules.legalMoves(afterAce, 0);
        assertTrue(legal.contains(new Move.PlayCard(c(Suit.HEARTS, Rank.SEVEN))), "replaying a card stays available");
        assertTrue(legal.contains(new Move.Pass()), "passing is offered as an alternative to replaying");

        MontanteState afterPass = (MontanteState) MontanteRules.applyMove(afterAce, 0, new Move.Pass());
        assertEquals(1, afterPass.currentPlayer(), "declining hands over to the next player");
        assertEquals(0, afterPass.passStreak(), "a voluntary decline is not a blocking pass");
    }

    @Test
    void pass_is_offered_after_chaining_two_aces() {
        MontanteState s = new MontanteState(
                List.of(
                        List.of(c(Suit.HEARTS, Rank.ACE), c(Suit.SPADES, Rank.ACE), c(Suit.HEARTS, Rank.SEVEN)),
                        List.of(c(Suit.CLUBS, Rank.TWO)),
                        List.of(),
                        List.of()),
                openedToKing(Suit.HEARTS)
                        .place(c(Suit.SPADES, Rank.EIGHT))
                        .place(c(Suit.SPADES, Rank.NINE))
                        .place(c(Suit.SPADES, Rank.TEN))
                        .place(c(Suit.SPADES, Rank.JACK))
                        .place(c(Suit.SPADES, Rank.QUEEN))
                        .place(c(Suit.SPADES, Rank.KING)),
                List.of(),
                0,
                0);
        MontanteState a1 = (MontanteState) MontanteRules.applyMove(s, 0, new Move.PlayCard(c(Suit.HEARTS, Rank.ACE)));
        MontanteState a2 = (MontanteState) MontanteRules.applyMove(a1, 0, new Move.PlayCard(c(Suit.SPADES, Rank.ACE)));
        assertEquals(0, a2.currentPlayer(), "two Aces keep the turn");

        assertTrue(MontanteRules.legalMoves(a2, 0).contains(new Move.Pass()), "pass is offered after the second Ace");
        MontanteState afterPass = (MontanteState) MontanteRules.applyMove(a2, 0, new Move.Pass());
        assertEquals(1, afterPass.currentPlayer(), "declining after two Aces hands over to the next player");
    }

    @Test
    void ace_with_no_remaining_legal_move_ends_the_turn() {
        MontanteState s = new MontanteState(
                List.of(
                        List.of(c(Suit.HEARTS, Rank.ACE), c(Suit.SPADES, Rank.TWO)),
                        List.of(c(Suit.CLUBS, Rank.TWO)),
                        List.of(),
                        List.of()),
                openedToKing(Suit.HEARTS),
                List.of(),
                0,
                0);
        MontanteState after =
                (MontanteState) MontanteRules.applyMove(s, 0, new Move.PlayCard(c(Suit.HEARTS, Rank.ACE)));
        assertEquals(1, after.currentPlayer());
    }

    @Test
    void ace_that_empties_the_hand_finishes_without_replay() {
        MontanteState s = new MontanteState(
                List.of(List.of(c(Suit.HEARTS, Rank.ACE)), List.of(c(Suit.CLUBS, Rank.TWO)), List.of(), List.of()),
                openedToKing(Suit.HEARTS),
                List.of(),
                0,
                0);
        MontanteState after =
                (MontanteState) MontanteRules.applyMove(s, 0, new Move.PlayCard(c(Suit.HEARTS, Rank.ACE)));
        assertEquals(List.of(0), after.finishingOrder());
        assertEquals(1, after.currentPlayer());
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
