package com.barbu.engine.round;

import com.barbu.engine.card.Card;
import com.barbu.engine.card.Suit;
import java.util.ArrayList;
import java.util.List;

public record Trick(int leader, List<Card> cards, int playerCount) {
    public Trick {
        cards = List.copyOf(cards);
    }

    public static Trick startedBy(int leader, int playerCount) {
        return new Trick(leader, List.of(), playerCount);
    }

    public boolean isComplete() {
        return cards.size() == playerCount;
    }

    public Suit ledSuit() {
        return cards.isEmpty() ? null : cards.get(0).suit();
    }

    public Trick withCard(Card card) {
        List<Card> next = new ArrayList<>(cards);
        next.add(card);
        return new Trick(leader, next, playerCount);
    }

    public int playerAt(int index) {
        return (leader + index) % playerCount;
    }

    /** Seat that takes the trick: the highest card of the led suit. In Barbu's negative
     *  contracts this is usually a penalty, not a win — so it is the "taker", not a "winner". */
    public int taker() {
        Suit led = ledSuit();
        int bestIndex = 0;
        int bestStrength = -1;
        for (int i = 0; i < cards.size(); i++) {
            Card c = cards.get(i);
            if (c.suit() == led && c.rank().trickStrength() > bestStrength) {
                bestStrength = c.rank().trickStrength();
                bestIndex = i;
            }
        }
        return playerAt(bestIndex);
    }
}
