package com.barbu.engine.model;

/**
 * Centralized, tunable scoring (spec §2.4). Changing the game balance means editing
 * this class only — never the rules logic.
 */
public final class ScoringConfig {
    private ScoringConfig() {
    }

    public static final int PER_TRICK = -2;
    public static final int PER_HEART = -2;
    public static final int PER_QUEEN = -6;
    public static final int PER_RED_KING = -10;

    public static final int MONTANTE_STEP = 5;

    /** {@code n} zero-sum places, linear and symmetric: place p gets (n-1-2p) * step. */
    public static int[] montanteRanking(int n) {
        int[] ranking = new int[n];
        for (int p = 0; p < n; p++) {
            ranking[p] = (n - 1 - 2 * p) * MONTANTE_STEP;
        }
        return ranking;
    }
}
