package com.barbu.engine;

import static org.junit.jupiter.api.Assertions.*;

import com.barbu.engine.card.Card;
import com.barbu.engine.card.Deck;
import com.barbu.engine.model.Contract;
import com.barbu.engine.round.MontanteRules;
import com.barbu.engine.round.MontanteState;
import com.barbu.engine.round.RoundEngine;
import com.barbu.engine.round.RoundState;
import com.barbu.engine.round.TrickTakingState;
import com.barbu.engine.scoring.TrickOutcome;
import com.barbu.engine.scoring.TrickScoringRule;
import com.barbu.engine.variant.Variant;
import com.barbu.engine.variant.Variants;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;

class RunningScoreInvariantsTest {

    private static List<List<Card>> deal(int n, long seed) {
        List<Card> deck = Deck.reducedShuffled(n, seed);
        int perHand = deck.size() / n;
        List<List<Card>> hands = new ArrayList<>();
        for (int seat = 0; seat < n; seat++) {
            hands.add(new ArrayList<>(deck.subList(seat * perHand, (seat + 1) * perHand)));
        }
        return hands;
    }

    // V1 (locked/monotone) at every trick boundary + V2 (running == final) at the end.
    private static void assertLockedTrickInvariant(Variant variant, Contract contract, int n, long seed) {
        TrickScoringRule rule = variant.trickRules().get(contract);
        RoundState s = RoundEngine.startTrickTaking(contract, deal(n, seed), 1);
        int[] prev = new int[n];
        while (!s.isComplete()) {
            TrickTakingState t = (TrickTakingState) s;
            if (t.currentTrick().cards().isEmpty()) { // boundary: hands are the true remaining tricks
                int[] running = rule.runningScore(new TrickOutcome(t.captured(), t.trickTakers(), n), t.hands());
                for (int seat = 0; seat < n; seat++) {
                    assertTrue(
                            Math.abs(running[seat]) >= Math.abs(prev[seat]),
                            "running shrank: " + contract + " n=" + n + " seed=" + seed + " seat=" + seat);
                }
                prev = running;
            }
            s = RoundEngine.applyMove(
                    s,
                    s.currentPlayer(),
                    RoundEngine.legalMoves(s, s.currentPlayer()).get(0));
        }
        TrickTakingState end = (TrickTakingState) s;
        TrickOutcome outcome = new TrickOutcome(end.captured(), end.trickTakers(), n);
        assertArrayEquals(
                rule.score(outcome),
                rule.runningScore(outcome, end.hands()),
                "running != final: " + contract + " n=" + n + " seed=" + seed);
    }

    @Property
    void developer_contracts_lock_running_scores(
            @ForAll @IntRange(min = 2, max = 10) int n, @ForAll @LongRange(min = 0, max = 200) long seed) {
        for (Contract c : List.of(Contract.NO_TRICKS, Contract.NO_HEARTS, Contract.NO_QUEENS, Contract.NO_RED_KINGS)) {
            assertLockedTrickInvariant(Variants.DEVELOPER, c, n, seed);
        }
    }

    @Property
    void classic_contracts_lock_running_scores(
            @ForAll @IntRange(min = 2, max = 10) int n, @ForAll @LongRange(min = 0, max = 200) long seed) {
        for (Contract c : List.of(
                Contract.NO_TRICKS,
                Contract.NO_HEARTS,
                Contract.NO_QUEENS,
                Contract.NO_KING_OF_HEARTS,
                Contract.NO_LAST_TWO_TRICKS,
                Contract.SALADE)) {
            assertLockedTrickInvariant(Variants.CLASSIC, c, n, seed);
        }
    }

    @Property
    void montante_locks_finished_seats_and_zeros_the_rest(
            @ForAll @IntRange(min = 2, max = 10) int n, @ForAll @LongRange(min = 0, max = 200) long seed) {
        RoundState s = RoundEngine.startMontante(deal(n, seed), 0);
        int[] prev = new int[n];
        int guard = 0;
        while (!s.isComplete()) {
            MontanteState m = (MontanteState) s;
            int[] running = MontanteRules.runningScore(m);
            for (int seat = 0; seat < n; seat++) {
                assertTrue(
                        Math.abs(running[seat]) >= Math.abs(prev[seat]),
                        "montante running shrank n=" + n + " seed=" + seed + " seat=" + seat);
                if (!m.hasFinished(seat)) {
                    assertEquals(0, running[seat], "unfinished seat must score 0 n=" + n + " seed=" + seed);
                }
            }
            prev = running;
            s = RoundEngine.applyMove(
                    s,
                    s.currentPlayer(),
                    RoundEngine.legalMoves(s, s.currentPlayer()).get(0));
            assertTrue(guard++ < 400, "montante did not terminate n=" + n + " seed=" + seed);
        }
    }

    private static List<String> heartsRunningTrace(int n, long seed) {
        TrickScoringRule rule = Variants.DEVELOPER.trickRules().get(Contract.NO_HEARTS);
        RoundState s = RoundEngine.startTrickTaking(Contract.NO_HEARTS, deal(n, seed), 1);
        List<String> trace = new ArrayList<>();
        while (!s.isComplete()) {
            TrickTakingState t = (TrickTakingState) s;
            if (t.currentTrick().cards().isEmpty()) {
                trace.add(Arrays.toString(
                        rule.runningScore(new TrickOutcome(t.captured(), t.trickTakers(), n), t.hands())));
            }
            s = RoundEngine.applyMove(
                    s,
                    s.currentPlayer(),
                    RoundEngine.legalMoves(s, s.currentPlayer()).get(0));
        }
        return trace;
    }

    @Property
    void running_scores_are_deterministic_for_a_seed(
            @ForAll @IntRange(min = 2, max = 10) int n, @ForAll @LongRange(min = 0, max = 200) long seed) {
        assertEquals(heartsRunningTrace(n, seed), heartsRunningTrace(n, seed));
    }
}
