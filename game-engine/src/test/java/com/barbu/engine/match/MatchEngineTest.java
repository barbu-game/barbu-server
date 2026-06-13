package com.barbu.engine.match;

import static org.junit.jupiter.api.Assertions.*;

import com.barbu.engine.card.Card;
import com.barbu.engine.card.Rank;
import com.barbu.engine.card.Suit;
import com.barbu.engine.model.Contract;
import com.barbu.engine.model.Move;
import com.barbu.engine.model.ScoringConfig;
import com.barbu.engine.round.RoundEngine;
import com.barbu.engine.round.RoundState;
import com.barbu.engine.round.Trick;
import com.barbu.engine.round.TrickTakingState;
import com.barbu.engine.variant.Variants;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MatchEngineTest {

    private static Card c(Suit s, Rank r) {
        return new Card(s, r);
    }

    private static MatchState matchWith(RoundState round) {
        int n = round.playerCount();
        return new MatchState(
                n, 1L, 0, 0, 100, EnumSet.noneOf(Contract.class), round, new int[n], List.of(), Variants.DEVELOPER);
    }

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
        List<Contract> seq = m.variant().contracts();
        for (Contract expected : seq) {
            assertEquals(expected, MatchEngine.nextContract(m));
            m = MatchEngine.playOut(MatchEngine.startNextContract(m));
        }
        // dealer has rotated; the imposed sequence restarts at the first contract
        assertEquals(seq.get(0), MatchEngine.nextContract(m));
    }

    @Test
    void dealer_rotates_after_five_contracts_and_marks_boundary() {
        MatchState m = MatchEngine.newMatch(3, 1L);
        for (int i = 0; i < 5; i++) {
            m = MatchEngine.playOut(MatchEngine.startNextContract(m));
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
            m = MatchEngine.playOut(MatchEngine.startNextContract(m));
            rounds++;
        }
        assertEquals(5, rounds);
    }

    @Test
    void a_full_match_completes_and_ranks_everyone() {
        for (int n = 2; n <= 6; n++) {
            MatchState m = MatchEngine.newMatch(n, 2024L);
            while (!MatchEngine.isComplete(m)) {
                m = MatchEngine.playOut(MatchEngine.startNextContract(m));
            }
            assertEquals(5 * n, m.roundNumber(), "n=" + n);
            List<Integer> standings = MatchEngine.standings(m);
            Set<Integer> expected = new HashSet<>();
            for (int s = 0; s < n; s++) expected.add(s);
            assertEquals(expected, new HashSet<>(standings), "n=" + n);
        }
    }

    @Test
    void round_is_over_once_a_card_contract_has_no_penalty_cards_left_in_hand() {
        List<List<Card>> hands = List.of(List.of(c(Suit.SPADES, Rank.TWO)), List.of(c(Suit.SPADES, Rank.THREE)));
        List<List<Card>> captured = List.of(List.of(c(Suit.HEARTS, Rank.KING), c(Suit.DIAMONDS, Rank.KING)), List.of());
        TrickTakingState round =
                new TrickTakingState(Contract.NO_RED_KINGS, hands, Trick.startedBy(0, 2), captured, List.of(0), 0);
        MatchState m = matchWith(round);
        assertFalse(round.isComplete());
        assertTrue(MatchEngine.roundOver(m));
    }

    @Test
    void per_trick_contract_runs_to_the_last_card() {
        List<List<Card>> hands = List.of(List.of(c(Suit.SPADES, Rank.TWO)), List.of(c(Suit.SPADES, Rank.THREE)));
        TrickTakingState round = new TrickTakingState(
                Contract.NO_TRICKS, hands, Trick.startedBy(0, 2), List.of(List.of(), List.of()), List.of(), 0);
        MatchState m = matchWith(round);
        assertFalse(MatchEngine.roundOver(m));
        assertSame(m, MatchEngine.settle(m));
    }

    @Test
    void settles_a_card_contract_early_and_scores_from_captured_cards() {
        List<List<Card>> hands = List.of(List.of(c(Suit.SPADES, Rank.TWO)), List.of(c(Suit.SPADES, Rank.THREE)));
        List<List<Card>> captured = List.of(List.of(c(Suit.HEARTS, Rank.KING), c(Suit.DIAMONDS, Rank.KING)), List.of());
        TrickTakingState round =
                new TrickTakingState(Contract.NO_RED_KINGS, hands, Trick.startedBy(0, 2), captured, List.of(0), 0);
        MatchState settled = MatchEngine.settle(matchWith(round));
        assertNull(settled.round());
        assertEquals(1, settled.roundNumber());
        assertEquals(2 * ScoringConfig.PER_RED_KING, settled.totals()[0]);
        assertEquals(0, settled.totals()[1]);
    }

    @Test
    void applying_the_capturing_move_settles_a_card_contract_before_the_hand_empties() {
        List<List<Card>> hands = List.of(
                List.of(c(Suit.HEARTS, Rank.KING), c(Suit.SPADES, Rank.TWO)),
                List.of(c(Suit.DIAMONDS, Rank.KING), c(Suit.SPADES, Rank.THREE)));
        MatchState m = matchWith(RoundEngine.startTrickTaking(Contract.NO_RED_KINGS, hands, 0));
        m = MatchEngine.applyMove(m, 0, new Move.PlayCard(c(Suit.HEARTS, Rank.KING)));
        m = MatchEngine.applyMove(m, 1, new Move.PlayCard(c(Suit.DIAMONDS, Rank.KING)));
        assertNull(m.round(), "both red kings captured, so the round settles without playing the spades");
        assertEquals(2 * ScoringConfig.PER_RED_KING, m.totals()[0]);
        assertEquals(0, m.totals()[1]);
    }
}
