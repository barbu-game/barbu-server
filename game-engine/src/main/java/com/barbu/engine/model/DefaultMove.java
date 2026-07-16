package com.barbu.engine.model;

import com.barbu.engine.card.Card;
import com.barbu.engine.card.Suit;
import java.util.List;

/**
 * Deterministic "default" move played when a seat does not act in time: the lowest legal card
 * (tie-broken by a stable suit order, black before red), or "pass" if there is no playable card
 * (the montante case where nothing can be laid down).
 */
public final class DefaultMove {

    private DefaultMove() {}

    public static Move pick(List<Move> legal) {
        Move.PlayCard best = null;
        for (Move move : legal) {
            if (move instanceof Move.PlayCard candidate && (best == null || isLower(candidate.card(), best.card()))) {
                best = candidate;
            }
        }
        return best != null ? best : new Move.Pass();
    }

    private static boolean isLower(Card a, Card b) {
        int ra = a.rank().trickStrength();
        int rb = b.rank().trickStrength();
        return ra != rb ? ra < rb : suitOrder(a.suit()) < suitOrder(b.suit());
    }

    private static int suitOrder(Suit suit) {
        return switch (suit) {
            case SPADES -> 0;
            case CLUBS -> 1;
            case HEARTS -> 2;
            case DIAMONDS -> 3;
        };
    }
}
