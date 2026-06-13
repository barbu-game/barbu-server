package com.barbu.app.rating;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

/** Construit l'{@link EloConfig} depuis la configuration (préfixe {@code barbu.elo}). */
@Factory
public class RatingConfiguration {

    @Singleton
    EloConfig eloConfig(
            @Value("${barbu.elo.initial-rating:1000}") int initialRating,
            @Value("${barbu.elo.k-factor:32}") int kFactor,
            @Value("${barbu.elo.provisional-k-factor:64}") int provisionalKFactor,
            @Value("${barbu.elo.provisional-games:10}") int provisionalGames,
            @Value("${barbu.elo.bot-rating:900}") int botRating,
            @Value("${barbu.elo.bot-fill-max-rating:1100}") int botFillMaxRating,
            @Value("${barbu.elo.initial-window:100}") int initialWindow,
            @Value("${barbu.elo.window-width-per-step:50}") int windowWidthPerStep,
            @Value("${barbu.elo.window-step-ms:5000}") long windowStepMs,
            @Value("${barbu.elo.ranked-bot-fill-ms:20000}") long rankedBotFillMs,
            @Value("${barbu.elo.ranked-table-size:4}") int rankedTableSize) {
        return new EloConfig(
                initialRating,
                kFactor,
                provisionalKFactor,
                provisionalGames,
                botRating,
                botFillMaxRating,
                initialWindow,
                windowWidthPerStep,
                windowStepMs,
                rankedBotFillMs,
                rankedTableSize);
    }
}
