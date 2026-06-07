package com.barbu.engine.card;

public enum Rank {
    TWO(2, 2),
    THREE(3, 3),
    FOUR(4, 4),
    FIVE(5, 5),
    SIX(6, 6),
    SEVEN(7, 7),
    EIGHT(8, 8),
    NINE(9, 9),
    TEN(10, 10),
    JACK(11, 11),
    QUEEN(12, 12),
    KING(13, 13),
    ACE(14, 1);

    private final int trickStrength;
    private final int montanteValue;

    Rank(int trickStrength, int montanteValue) {
        this.trickStrength = trickStrength;
        this.montanteValue = montanteValue;
    }

    public int trickStrength() {
        return trickStrength;
    }

    public int montanteValue() {
        return montanteValue;
    }
}
