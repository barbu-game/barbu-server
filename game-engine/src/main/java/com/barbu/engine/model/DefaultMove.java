package com.barbu.engine.model;

import com.barbu.engine.card.Card;
import com.barbu.engine.card.Suit;
import java.util.List;

/**
 * Coup « par défaut » déterministe joué quand un siège n'agit pas à temps : la carte légale la
 * plus faible (départage par couleur stable, noir avant rouge), ou « passe » s'il n'y a aucune
 * carte jouable (cas montante où l'on ne peut rien poser).
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
