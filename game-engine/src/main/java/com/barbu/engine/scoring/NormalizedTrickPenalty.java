package com.barbu.engine.scoring;

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
        List<Integer> takers = outcome.trickTakers();
        int[] shares = ScoringConfig.distribute(ScoringConfig.POINTS_PER_ROUND, takers.size());
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
