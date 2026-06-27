package com.barbu.engine.scoring;

import com.barbu.engine.card.Card;
import java.util.List;
import java.util.stream.Collectors;

/** Sum of several rules, term by term (used for the "salad" contract). */
public record CombinedRule(List<TrickScoringRule> rules) implements TrickScoringRule {

    @Override
    public int[] score(TrickOutcome outcome) {
        int[] points = new int[outcome.playerCount()];
        for (TrickScoringRule rule : rules) {
            int[] partial = rule.score(outcome);
            for (int seat = 0; seat < points.length; seat++) {
                points[seat] += partial[seat];
            }
        }
        return points;
    }

    /** Term-by-term sum of each component's running score. */
    @Override
    public int[] runningScore(TrickOutcome captured, List<List<Card>> remainingHands) {
        int[] points = new int[captured.playerCount()];
        for (TrickScoringRule rule : rules) {
            int[] partial = rule.runningScore(captured, remainingHands);
            for (int seat = 0; seat < points.length; seat++) {
                points[seat] += partial[seat];
            }
        }
        return points;
    }

    @Override
    public String describe() {
        return rules.stream().map(TrickScoringRule::describe).collect(Collectors.joining("; "));
    }

    /** Exhausted only when every component is — a single per-trick term keeps the round running. */
    @Override
    public boolean exhausted(List<List<Card>> remainingHands) {
        return rules.stream().allMatch(rule -> rule.exhausted(remainingHands));
    }
}
