package com.barbu.engine.model;

/**
 * Centralized, tunable scoring. Changing the game balance means editing this class
 * only — never the rules logic.
 */
public final class ScoringConfig {
    private ScoringConfig() {}

    public static final int PER_TRICK = -2;
    public static final int PER_HEART = -2;
    public static final int PER_QUEEN = -6;
    public static final int PER_RED_KING = -10;
    public static final int PER_KING_OF_HEARTS = -20;
    public static final int PER_JACK = -2;
    public static final int PER_LAST_TRICK = -10;

    /** Every penalty contract hands out exactly this many points, spread over its scoring units. */
    public static final int POINTS_PER_ROUND = 60;

    public static final int MONTANTE_TOP = 30;

    /**
     * {@code n} zero-sum places, linearly interpolated from {@code +MONTANTE_TOP} (first) to
     * {@code -MONTANTE_TOP} (last). Mirrored around the centre so the sum is always zero.
     */
    public static int[] montanteRanking(int n) {
        int[] ranking = new int[n];
        for (int p = 0; 2 * p < n; p++) {
            int value = Math.round((float) MONTANTE_TOP * (n - 1 - 2 * p) / (n - 1));
            ranking[p] = value;
            ranking[n - 1 - p] = -value;
        }
        return ranking;
    }

    /**
     * Split {@code total} into {@code units} integer shares that sum to exactly {@code total},
     * each {@code floor(total/units)} or one more, with the remainder spread as evenly as
     * possible across the units (so no run of units is favoured over another).
     */
    public static int[] distribute(int total, int units) {
        if (units <= 0) {
            return new int[0];
        }
        int base = total / units;
        int remainder = total % units;
        int[] shares = new int[units];
        for (int i = 0; i < units; i++) {
            int extra = ((i + 1) * remainder) / units - (i * remainder) / units;
            shares[i] = base + extra;
        }
        return shares;
    }
}
