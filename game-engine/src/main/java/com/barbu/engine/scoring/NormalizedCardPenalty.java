package com.barbu.engine.scoring;

import com.barbu.engine.card.Card;
import com.barbu.engine.model.ScoringConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Spreads exactly {@link ScoringConfig#POINTS_PER_ROUND} over every captured card matching
 * {@code match} (e.g. each heart, each queen), so a contract always hands out the same total
 * whatever the deck size. When the count does not divide the total, the leftover point is spread
 * uniformly across the cards in rank order — capturing more or stronger cards is never singled out.
 */
public record NormalizedCardPenalty(Predicate<Card> match, String label) implements TrickScoringRule {

    private static final Comparator<Card> CANONICAL =
            Comparator.comparingInt((Card c) -> c.rank().trickStrength()).thenComparing(Card::suit);

    @Override
    public int[] score(TrickOutcome outcome) {
        return spreadOver(outcome, List.of());
    }

    /**
     * The captured cards keep the share they will carry at the end of the round: the total is spread
     * over every matching card in play (those captured plus the ones still in {@code remainingHands}),
     * so a seat's running penalty only grows and lands on its final value once the hands are empty.
     */
    @Override
    public int[] runningScore(TrickOutcome captured, List<List<Card>> remainingHands) {
        return spreadOver(captured, remainingHands);
    }

    /**
     * Penalize the captured matching cards, spreading the total over the captured ones plus every
     * matching card still held in {@code extraHands} (empty for the final score).
     */
    private int[] spreadOver(TrickOutcome outcome, List<List<Card>> extraHands) {
        List<Card> universe = new ArrayList<>();
        collectMatching(outcome.capturedPerSeat(), universe);
        collectMatching(extraHands, universe);
        universe.sort(CANONICAL);
        int[] shares = ScoringConfig.distribute(ScoringConfig.POINTS_PER_ROUND, universe.size());
        Map<Card, Integer> penaltyByCard = new HashMap<>();
        for (int i = 0; i < universe.size(); i++) {
            penaltyByCard.put(universe.get(i), -shares[i]);
        }

        int[] points = new int[outcome.playerCount()];
        for (int seat = 0; seat < outcome.playerCount(); seat++) {
            int total = 0;
            for (Card card : outcome.capturedPerSeat().get(seat)) {
                if (match.test(card)) {
                    total += penaltyByCard.get(card);
                }
            }
            points[seat] = total;
        }
        return points;
    }

    private void collectMatching(List<List<Card>> hands, List<Card> into) {
        for (List<Card> hand : hands) {
            for (Card card : hand) {
                if (match.test(card)) {
                    into.add(card);
                }
            }
        }
    }

    @Override
    public String describe() {
        return ScoringConfig.POINTS_PER_ROUND + " spread over the " + label + "s";
    }

    /** All scoring cards are already captured once none remain in any hand. */
    @Override
    public boolean exhausted(List<List<Card>> remainingHands) {
        return remainingHands.stream().flatMap(List::stream).noneMatch(match);
    }
}
