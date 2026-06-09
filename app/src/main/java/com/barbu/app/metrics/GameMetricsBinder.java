package com.barbu.app.metrics;

import com.barbu.app.room.RoomManager;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micronaut.context.annotation.Context;
import jakarta.annotation.PostConstruct;

/**
 * Registers live gauges sourced from the in-memory room state. {@code @Context} makes the
 * bean eager so the gauges are registered at startup even though nothing injects it.
 */
@Context
public class GameMetricsBinder {

    private final MeterRegistry registry;
    private final RoomManager rooms;

    public GameMetricsBinder(MeterRegistry registry, RoomManager rooms) {
        this.registry = registry;
        this.rooms = rooms;
    }

    @PostConstruct
    void bind() {
        Gauge.builder("barbu.rooms.active", rooms, RoomManager::activeRoomCount)
                .description("Active game rooms")
                .register(registry);
        Gauge.builder("barbu.players.active", rooms, RoomManager::activeHumanCount)
                .description("Connected human players seated in rooms")
                .register(registry);
        Gauge.builder("barbu.bots.active", rooms, RoomManager::activeBotCount)
                .description("Bot players seated in rooms")
                .register(registry);
    }
}
