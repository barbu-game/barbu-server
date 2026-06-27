package com.barbu.engine;

import static org.junit.jupiter.api.Assertions.*;

import com.barbu.engine.card.Card;
import com.barbu.engine.card.Deck;
import com.barbu.engine.match.MatchEngine;
import com.barbu.engine.model.Contract;
import com.barbu.engine.round.RoundEngine;
import com.barbu.engine.round.RoundState;
import com.barbu.engine.round.TrickTakingState;
import com.barbu.engine.scoring.TrickScoringRule;
import com.barbu.engine.variant.Variants;
import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;

class RoundInvariantsTest {

    private static List<List<Card>> deal(int n, long seed) {
        List<Card> deck = Deck.reducedShuffled(n, seed);
        int perHand = deck.size() / n;
        List<List<Card>> hands = new ArrayList<>();
        for (int seat = 0; seat < n; seat++) {
            hands.add(new ArrayList<>(deck.subList(seat * perHand, (seat + 1) * perHand)));
        }
        return hands;
    }

    @Property
    void trick_taking_always_offers_a_legal_move_until_complete(
            @ForAll @IntRange(min = 2, max = 10) int n, @ForAll @LongRange(min = 0, max = 800) long seed) {
        RoundState s = RoundEngine.startTrickTaking(Contract.NO_TRICKS, deal(n, seed), 1);
        while (!s.isComplete()) {
            List<com.barbu.engine.model.Move> legal = RoundEngine.legalMoves(s, s.currentPlayer());
            assertFalse(legal.isEmpty(), "n=" + n + " seed=" + seed);
            s = RoundEngine.applyMove(s, s.currentPlayer(), legal.get(0));
        }
    }

    @Property
    void no_tricks_hands_out_exactly_sixty_points(
            @ForAll @IntRange(min = 2, max = 10) int n, @ForAll @LongRange(min = 0, max = 400) long seed) {
        RoundState s = RoundEngine.startTrickTaking(Contract.NO_TRICKS, deal(n, seed), 1);
        while (!s.isComplete()) {
            s = RoundEngine.applyMove(
                    s,
                    s.currentPlayer(),
                    RoundEngine.legalMoves(s, s.currentPlayer()).get(0));
        }
        int total = 0;
        for (int p : MatchEngine.scoreRound(Variants.DEVELOPER, s).points()) {
            total += p;
        }
        assertEquals(-60, total, "n=" + n + " seed=" + seed);
    }

    @Property
    void stopping_a_card_contract_early_scores_the_same_as_playing_to_the_end(
            @ForAll @IntRange(min = 2, max = 10) int n, @ForAll @LongRange(min = 0, max = 400) long seed) {
        TrickScoringRule rule = Variants.DEVELOPER.trickRules().get(Contract.NO_RED_KINGS);
        RoundState s = RoundEngine.startTrickTaking(Contract.NO_RED_KINGS, deal(n, seed), 1);
        int[] scoreAtExhaustion = null;
        while (!s.isComplete()) {
            TrickTakingState t = (TrickTakingState) s;
            boolean atBoundary =
                    t.currentTrick().isComplete() || t.currentTrick().cards().isEmpty();
            if (scoreAtExhaustion == null && atBoundary && rule.exhausted(t.hands())) {
                scoreAtExhaustion =
                        MatchEngine.scoreRound(Variants.DEVELOPER, s).points();
            }
            s = RoundEngine.applyMove(
                    s,
                    s.currentPlayer(),
                    RoundEngine.legalMoves(s, s.currentPlayer()).get(0));
        }
        if (scoreAtExhaustion != null) {
            int[] finalScore = MatchEngine.scoreRound(Variants.DEVELOPER, s).points();
            assertArrayEquals(scoreAtExhaustion, finalScore, "n=" + n + " seed=" + seed);
        }
    }

    @Property
    void montante_always_terminates_and_is_zero_sum(
            @ForAll @IntRange(min = 2, max = 10) int n, @ForAll @LongRange(min = 0, max = 400) long seed) {
        List<List<Card>> hands = deal(n, seed);
        RoundState s = RoundEngine.startMontante(hands, RoundEngine.eightOfDiamondsHolder(hands));
        int guard = 0;
        while (!s.isComplete()) {
            s = RoundEngine.applyMove(
                    s,
                    s.currentPlayer(),
                    RoundEngine.legalMoves(s, s.currentPlayer()).get(0));
            assertTrue(guard++ < 400, "montante did not terminate n=" + n + " seed=" + seed);
        }
        int total = 0;
        for (int p : MatchEngine.scoreRound(Variants.DEVELOPER, s).points()) {
            total += p;
        }
        assertEquals(0, total, "n=" + n + " seed=" + seed);
    }
}
