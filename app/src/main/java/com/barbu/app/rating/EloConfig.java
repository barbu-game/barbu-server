package com.barbu.app.rating;

/**
 * Tunable parameters for ELO rating and ranked matchmaking. Pure record (no Micronaut dependency)
 * so it stays injectable into the computation tested in isolation.
 */
public record EloConfig(
        int initialRating,
        int kFactor,
        int provisionalKFactor,
        int provisionalGames,
        int botRating,
        int botFillMaxRating,
        int initialWindow,
        int windowWidthPerStep,
        long windowStepMs,
        long rankedBotFillMs,
        int rankedTableSize) {

    public static EloConfig defaults() {
        return new EloConfig(1000, 32, 64, 10, 900, 1100, 100, 50, 5000L, 20000L, 4);
    }
}
