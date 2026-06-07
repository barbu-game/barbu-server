package com.barbu.engine.card;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public final class Deck {
    private Deck() {}

    private static final List<Card> REMOVAL_ORDER = buildRemovalOrder();

    public static List<Card> full() {
        List<Card> cards = new ArrayList<>(52);
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                cards.add(new Card(suit, rank));
            }
        }
        return cards;
    }

    /** Weakest first; red before black at equal rank. The first {@code 52 mod N} are dropped. */
    public static List<Card> removalOrder() {
        return REMOVAL_ORDER;
    }

    public static List<Card> reducedShuffled(int playerCount, long seed) {
        List<Card> cards = full();
        int toRemove = cards.size() % playerCount;
        cards.removeAll(REMOVAL_ORDER.subList(0, toRemove));
        Collections.shuffle(cards, new Random(seed));
        return List.copyOf(cards);
    }

    private static List<Card> buildRemovalOrder() {
        List<Card> order = full();
        order.sort(Comparator.comparingInt((Card c) -> c.rank().trickStrength())
                .thenComparingInt(c -> suitRemovalPriority(c.suit())));
        return List.copyOf(order);
    }

    private static int suitRemovalPriority(Suit suit) {
        return switch (suit) {
            case HEARTS -> 0;
            case DIAMONDS -> 1;
            case CLUBS -> 2;
            case SPADES -> 3;
        };
    }
}
