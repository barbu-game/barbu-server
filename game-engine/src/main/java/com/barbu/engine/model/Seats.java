package com.barbu.engine.model;

public final class Seats {
    private Seats() {
    }

    public static final int MIN = 2;
    public static final int MAX = 10;

    public static int next(int seat, int playerCount) {
        return (seat + 1) % playerCount;
    }
}
