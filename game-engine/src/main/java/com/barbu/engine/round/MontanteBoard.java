package com.barbu.engine.round;

import com.barbu.engine.card.Card;
import com.barbu.engine.card.Suit;

public record MontanteBoard(int[] low, int[] high) {

    private static final int OPENING_VALUE = 8;

    public MontanteBoard {
        low = low.clone();
        high = high.clone();
    }

    public static MontanteBoard empty() {
        return new MontanteBoard(new int[Suit.values().length], new int[Suit.values().length]);
    }

    public boolean isOpened(Suit suit) {
        return low[suit.ordinal()] != 0;
    }

    public boolean isPlayable(Card card) {
        int s = card.suit().ordinal();
        int v = card.rank().montanteValue();
        if (low[s] == 0) {
            return v == OPENING_VALUE;
        }
        return v == low[s] - 1 || v == high[s] + 1;
    }

    public MontanteBoard place(Card card) {
        if (!isPlayable(card)) {
            throw new IllegalArgumentException("not playable: " + card);
        }
        int s = card.suit().ordinal();
        int v = card.rank().montanteValue();
        int[] nl = low.clone();
        int[] nh = high.clone();
        if (nl[s] == 0) {
            nl[s] = v;
            nh[s] = v;
        } else if (v == nl[s] - 1) {
            nl[s] = v;
        } else {
            nh[s] = v;
        }
        return new MontanteBoard(nl, nh);
    }
}
