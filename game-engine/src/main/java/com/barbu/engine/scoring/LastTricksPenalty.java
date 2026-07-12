package com.barbu.engine.scoring;

import com.barbu.engine.card.Card;
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

    /**
     * Only the tricks certain to be among the round's last {@code n} count: their absolute index is
     * known from the total trick count (played plus those still in {@code remainingHands}). A trick
     * played before that boundary contributes nothing, so a seat's penalty never appears then vanishes.
     */
    @Override
    public int[] runningScore(TrickOutcome captured, List<List<Card>> remainingHands) {
        int tricksToCome = remainingHands.stream().mapToInt(List::size).max().orElse(0);
        int totalTricks = captured.trickTakers().size() + tricksToCome;
        int firstPenalised = Math.max(0, totalTricks - n);
        int[] points = new int[captured.playerCount()];
        List<Integer> takers = captured.trickTakers();
        for (int i = firstPenalised; i < takers.size(); i++) {
            points[takers.get(i)] += pointsPerTrick;
        }
        return points;
    }

    @Override
    public String describe() {
        return pointsPerTrick + " per trick among the last " + n;
    }
}
