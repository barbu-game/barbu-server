package com.barbu.engine.scoring;

import com.barbu.engine.card.Card;
import java.util.List;
import java.util.function.Predicate;

/** Penalty per captured card matching {@code match} (e.g. each heart, each queen). */
public record CardPenalty(Predicate<Card> match, int pointsPerCard, String label) implements TrickScoringRule {

    @Override
    public int[] score(TrickOutcome outcome) {
        int[] points = new int[outcome.playerCount()];
        for (int seat = 0; seat < outcome.playerCount(); seat++) {
            int hits = 0;
            for (Card card : outcome.capturedPerSeat().get(seat)) {
                if (match.test(card)) {
                    hits++;
                }
            }
            points[seat] = hits * pointsPerCard;
        }
        return points;
    }

    @Override
    public String describe() {
        return pointsPerCard + " per " + label;
    }

    /** All scoring cards are already captured once none remain in any hand. */
    @Override
    public boolean exhausted(List<List<Card>> remainingHands) {
        return remainingHands.stream().flatMap(List::stream).noneMatch(match);
    }
}
