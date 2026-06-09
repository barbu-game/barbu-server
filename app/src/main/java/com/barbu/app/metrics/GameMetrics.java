package com.barbu.app.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Singleton;

/** Cumulative game-lifecycle counters. Holds no reference to the room layer (no cycle). */
@Singleton
public class GameMetrics {

    private final Counter gamesStarted;
    private final Counter gamesFinished;

    public GameMetrics(MeterRegistry registry) {
        this.gamesStarted = Counter.builder("barbu.games.started")
                .description("Games started")
                .register(registry);
        this.gamesFinished = Counter.builder("barbu.games.finished")
                .description("Games finished (completed or stopped)")
                .register(registry);
    }

    public void gameStarted() {
        gamesStarted.increment();
    }

    public void gameFinished() {
        gamesFinished.increment();
    }
}
