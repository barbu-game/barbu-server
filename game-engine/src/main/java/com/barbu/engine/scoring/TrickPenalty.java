package com.barbu.engine.scoring;

/** Penalty per trick taken. */
public record TrickPenalty(int pointsPerTrick) implements TrickScoringRule {

    @Override
    public int[] score(TrickOutcome outcome) {
        int[] points = new int[outcome.playerCount()];
        for (int taker : outcome.trickTakers()) {
            points[taker] += pointsPerTrick;
        }
        return points;
    }

    @Override
    public String describe() {
        return pointsPerTrick + " per trick taken";
    }
}
