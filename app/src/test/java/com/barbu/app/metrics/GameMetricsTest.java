package com.barbu.app.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class GameMetricsTest {

    @Test
    void counts_rehydrations() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        GameMetrics metrics = new GameMetrics(registry);

        metrics.rehydrated();
        metrics.rehydrated();

        assertEquals(2.0, registry.get("barbu.rehydrations").counter().count());
    }
}
