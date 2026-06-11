package com.barbu.app.metrics;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@MicronautTest
class MetricsEndpointTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    com.barbu.app.room.RoomManager rooms;

    @Test
    void exposesPrometheusEndpointWithJvmMetrics() {
        String body = client.toBlocking().retrieve("/prometheus");
        assertTrue(body.contains("jvm_memory_used_bytes"), "JVM metrics should be exposed");
    }

    @Test
    void reportsActiveRoomsGauge() {
        rooms.create(4, com.barbu.engine.variant.Variants.DEVELOPER);
        String body = client.toBlocking().retrieve("/prometheus");
        assertTrue(body.contains("barbu_rooms_active"), "rooms gauge should be present");
        assertTrue(body.contains("barbu_players_active"), "players gauge should be present");
        assertTrue(body.contains("barbu_bots_active"), "bots gauge should be present");
    }

    @Test
    void reportsMatchmakingQueueGauge() {
        String body = client.toBlocking().retrieve("/prometheus");
        assertTrue(body.contains("barbu_matchmaking_queue"), "matchmaking gauge should be present");
    }

    @Test
    void countsStartedGames() {
        com.barbu.app.room.GameRoom room = rooms.create(2, com.barbu.engine.variant.Variants.DEVELOPER);
        room.addBot();
        room.addBot();
        room.start(42L);
        String body = client.toBlocking().retrieve("/prometheus");
        assertTrue(body.contains("barbu_games_started_total"), "started counter should be present");
        assertTrue(body.contains("barbu_games_finished_total"), "finished counter should be present");
    }
}
