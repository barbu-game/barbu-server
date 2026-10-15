package com.barbu.rami.card;

import com.barbu.engine.card.Card;
import com.barbu.engine.card.Deck;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class RamiDeck {
    public static final int SIZE = 108;
    private static final int JOKERS = 4;

    private RamiDeck() {}

    public static List<RamiCard> shuffled(long seed) {
        List<RamiCard> cards = new ArrayList<>(SIZE);
        int id = 0;
        for (int copy = 0; copy < 2; copy++) {
            for (Card card : Deck.full()) {
                cards.add(new RamiCard(id++, card));
            }
        }
        for (int j = 0; j < JOKERS; j++) {
            cards.add(new RamiCard(id++, null));
        }
        Collections.shuffle(cards, new Random(seed));
        return List.copyOf(cards);
    }
}
