package com.barbu.engine.scoring;

import java.util.List;

/** Penalty applied to the takers of the last {@code n} tricks. */
public record LastTricksPenalty(int n, int pointsPerTrick) implements TrickScoringRule {

    @Override
    public int[] score(TrickOutcome outcome) {
        int[] points = new int[outcome.playerCount()];
        List<Integer> takers = outcome.trickTakers();
        int start = Math.max(0, takers.size() - n);
        for (int i = start; i < takers.size(); i++) {
            points[takers.get(i)] += pointsPerTrick;
        }
        return points;
    }

    @Override
    public String describe() {
        return pointsPerTrick + " per trick among the last " + n;
    }
}
