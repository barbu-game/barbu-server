package com.barbu.engine.scoring;

import com.barbu.engine.card.Card;
import com.barbu.engine.model.ScoringConfig;
import java.util.List;

/**
 * Spreads exactly {@link ScoringConfig#POINTS_PER_ROUND} over the tricks of the round, so the
 * "no tricks" contract always hands out the same total whatever the deck size. The leftover point,
 * when the trick count does not divide the total, is spread uniformly across the tricks in order.
 */
public record NormalizedTrickPenalty() implements TrickScoringRule {

    @Override
    public int[] score(TrickOutcome outcome) {
        return spreadOver(outcome, outcome.trickTakers().size());
    }

    /**
     * The captured tricks keep the share they will carry at the end of the round: the total is spread
     * over every trick of the round (those played plus the {@code remainingHands} still to come), so a
     * seat's running penalty only grows and lands on its final value once the hands are empty.
     */
    @Override
    public int[] runningScore(TrickOutcome captured, List<List<Card>> remainingHands) {
        int tricksToCome = remainingHands.stream().mapToInt(List::size).max().orElse(0);
        return spreadOver(captured, captured.trickTakers().size() + tricksToCome);
    }

    private static int[] spreadOver(TrickOutcome outcome, int totalTricks) {
        List<Integer> takers = outcome.trickTakers();
        int[] shares = ScoringConfig.distribute(ScoringConfig.POINTS_PER_ROUND, totalTricks);
        int[] points = new int[outcome.playerCount()];
        for (int trick = 0; trick < takers.size(); trick++) {
            points[takers.get(trick)] -= shares[trick];
        }
        return points;
    }

    @Override
    public String describe() {
        return ScoringConfig.POINTS_PER_ROUND + " spread over the tricks";
    }
}
