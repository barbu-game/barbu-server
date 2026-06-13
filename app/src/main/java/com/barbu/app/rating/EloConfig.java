package com.barbu.app.rating;

/**
 * Paramètres ajustables du classement ELO et du matchmaking ranked. Record pur (aucune
 * dépendance Micronaut) pour rester injectable dans le calcul testé en isolation.
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
