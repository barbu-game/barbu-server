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
        List<Card> matching = new ArrayList<>();
        for (List<Card> seatCards : outcome.capturedPerSeat()) {
            for (Card card : seatCards) {
                if (match.test(card)) {
                    matching.add(card);
                }
            }
        }
        matching.sort(CANONICAL);
        int[] shares = ScoringConfig.distribute(ScoringConfig.POINTS_PER_ROUND, matching.size());
        Map<Card, Integer> penaltyByCard = new HashMap<>();
        for (int i = 0; i < matching.size(); i++) {
            penaltyByCard.put(matching.get(i), -shares[i]);
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
