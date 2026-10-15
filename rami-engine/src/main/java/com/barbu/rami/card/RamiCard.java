package com.barbu.rami.card;

import com.barbu.engine.card.Card;
import com.barbu.engine.card.Rank;

/** A null {@code card} is a joker; ids tell apart the two copies of every card. */
public record RamiCard(int id, Card card) {

    public boolean isJoker() {
        return card == null;
    }

    public int penalty() {
        return isJoker() ? 25 : rankPoints(card.rank());
    }

    public static int rankPoints(Rank rank) {
        return switch (rank) {
            case ACE -> 11;
            case JACK, QUEEN, KING -> 10;
            default -> rank.trickStrength();
        };
    }
}
