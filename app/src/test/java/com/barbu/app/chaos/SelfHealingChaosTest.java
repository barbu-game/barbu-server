package com.barbu.app.chaos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.websocket.WebSocketClient;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Flux;

/**
 * Prouve le self-healing : une partie en cours survit à la perte du pod propriétaire. Deux instances
 * Micronaut (deux « pods ») partagent un Redis ; on tue le propriétaire, un client se reconnecte via
 * l'autre et retrouve exactement l'état de la table.
 */
@Testcontainers
class SelfHealingChaosTest {

    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    static String redisUri;

    @BeforeAll
    static void up() {
        redis.start();
        redisUri = "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379);
    }

    @AfterAll
    static void down() {
        redis.stop();
    }

    // Large bot delay → the dealt round stays stable (no bot move) during the test window.
    private EmbeddedServer instance(String podId) {
        return ApplicationContext.run(
                EmbeddedServer.class, Map.of("redis.uri", redisUri, "POD_ID", podId, "barbu.bot-delay-ms", 3_600_000));
    }

    private TestGameClient connect(EmbeddedServer server) {
        WebSocketClient client = server.getApplicationContext().createBean(WebSocketClient.class, server.getURL());
        return Flux.from(client.connect(TestGameClient.class, "/ws/game")).blockFirst();
    }

    @Test
    void game_survives_owner_pod_loss() throws Exception {
        EmbeddedServer a = instance("pod-A");
        EmbeddedServer b = instance("pod-B");

        TestGameClient client = connect(a);
        client.sendJson(Map.of("type", "createRoom", "name", "Alice", "playerCount", 4));
        JsonNode joined = client.await(m -> m.path("type").asText().equals("joined"));
        String roomId = joined.path("roomId").asText();
        client.sendJson(Map.of("type", "addBot"));
        client.sendJson(Map.of("type", "addBot"));
        client.sendJson(Map.of("type", "addBot"));
        client.sendJson(Map.of("type", "start"));

        // Wait for a DEALT round (non-null contract → hands dealt, a real in-progress state). The deal
        // fires ~3.5s after start; the large bot delay then keeps it stable (no moves).
        JsonNode before = client.await(m ->
                m.path("type").asText().equals("state") && m.hasNonNull("resumeToken") && m.hasNonNull("contract"));
        String resumeToken = before.path("resumeToken").asText();
        int roundBefore = before.path("roundNumber").asInt();
        String contractBefore = before.path("contract").asText();
        client.close();

        // CHAOS: the owning pod dies mid-game (shutdown releases its Redis leases).
        a.stop();

        // Reconnect via the surviving instance → rehydrate from the durable snapshot.
        TestGameClient recovered = connect(b);
        recovered.sendJson(Map.of("type", "resume", "resumeToken", resumeToken));
        recovered.await(m -> m.path("type").asText().equals("joined")
                && m.path("roomId").asText().equals(roomId));
        JsonNode after = recovered.await(m -> m.path("type").asText().equals("state") && m.hasNonNull("contract"));

        assertEquals(roundBefore, after.path("roundNumber").asInt());
        assertEquals(contractBefore, after.path("contract").asText());

        recovered.close();
        b.stop();
    }

    @Test
    void a_fresh_instance_rehydrates_the_table_via_redis() throws Exception {
        // Contrast (the "Compose would lose it" scenario, but proving the shared-state win): even after
        // the ONLY instance is replaced, a new instance rehydrates the table from Redis. Under Compose
        // (single host, no shared state) the same restart loses the game — documented in load/README.md.
        EmbeddedServer first = instance("pod-solo");
        TestGameClient client = connect(first);
        client.sendJson(Map.of("type", "createRoom", "name", "Bob", "playerCount", 4));
        client.await(m -> m.path("type").asText().equals("joined"));
        client.sendJson(Map.of("type", "addBot"));
        client.sendJson(Map.of("type", "addBot"));
        client.sendJson(Map.of("type", "addBot"));
        client.sendJson(Map.of("type", "start"));
        JsonNode state = client.await(m ->
                m.path("type").asText().equals("state") && m.hasNonNull("resumeToken") && m.hasNonNull("contract"));
        String token = state.path("resumeToken").asText();
        client.close();

        first.stop();

        EmbeddedServer replacement = instance("pod-solo-2");
        TestGameClient c2 = connect(replacement);
        c2.sendJson(Map.of("type", "resume", "resumeToken", token));
        JsonNode resumed = c2.await(m -> {
            String t = m.path("type").asText();
            return t.equals("state") || t.equals("resumeUnavailable");
        });

        assertTrue(resumed.path("type").asText().equals("state"), "table should rehydrate from Redis, got " + resumed);
        c2.close();
        replacement.stop();
    }
}
