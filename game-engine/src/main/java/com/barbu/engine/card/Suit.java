package com.barbu.engine.card;

public enum Suit {
    CLUBS, DIAMONDS, HEARTS, SPADES;

    public boolean isRed() {
        return this == HEARTS || this == DIAMONDS;
    }
}
