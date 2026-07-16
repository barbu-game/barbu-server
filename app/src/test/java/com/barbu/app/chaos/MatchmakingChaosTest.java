package com.barbu.app.chaos;

import static org.junit.jupiter.api.Assertions.*;

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
 * Proves the two gains vs docker-compose: (1) defragmentation — two players on DIFFERENT pods
 * form ONE table via the shared Redis queue; (2) durability — the queue survives a pod death, a
 * returning player re-enqueues and matches via the surviving pod.
 */
@Testcontainers
class MatchmakingChaosTest {

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

    private EmbeddedServer instance(String podId) {
        // Short entry TTL: after a pod dies, its player's orphaned queue entry expires quickly,
        // so the durability test is deterministic (no "stolen" match).
        return ApplicationContext.run(
                EmbeddedServer.class,
                Map.of(
                        "redis.uri",
                        redisUri,
                        "POD_ID",
                        podId,
                        "barbu.bot-delay-ms",
                        3_600_000,
                        "barbu.mm.entry-ttl-ms",
                        3_000));
    }

    private TestGameClient connect(EmbeddedServer server) {
        WebSocketClient client = server.getApplicationContext().createBean(WebSocketClient.class, server.getURL());
        return Flux.from(client.connect(TestGameClient.class, "/ws/game")).blockFirst();
    }

    private EmbeddedServer serverForPod(String pod, EmbeddedServer a, String podA, EmbeddedServer b, String podB) {
        return pod.equals(podA) ? a : (pod.equals(podB) ? b : null);
    }

    @Test
    void two_players_on_different_pods_form_one_table() throws Exception {
        EmbeddedServer a = instance("pod-A");
        EmbeddedServer b = instance("pod-B");
        try {
            TestGameClient ca = connect(a);
            TestGameClient cb = connect(b);
            ca.sendJson(Map.of("type", "enqueueMatchmaking", "name", "Alice", "size", 2));
            cb.sendJson(Map.of("type", "enqueueMatchmaking", "name", "Bob", "size", 2));

            JsonNode ma = ca.await(m -> m.path("type").asText().equals("matched"));
            JsonNode mb = cb.await(m -> m.path("type").asText().equals("matched"));
            assertEquals(
                    ma.path("roomId").asText(),
                    mb.path("roomId").asText(),
                    "players enqueued on different pods land in the SAME room (queue is global)");

            // Alice reclaims her reserved seat. If the table lives on the OTHER pod, `matched` carries
            // `pod` and she reconnects there; if it lives on her own pod (leader = pod-A), `pod` is omitted
            // and she reclaims on her current connection — exactly the contract the browser client follows.
            TestGameClient reclaimer;
            boolean reclaimerIsNew;
            if (ma.has("pod")) {
                EmbeddedServer owner = serverForPod(ma.path("pod").asText(), a, "pod-A", b, "pod-B");
                assertNotNull(owner, "owner pod is one of the two instances");
                reclaimer = connect(owner);
                reclaimerIsNew = true;
            } else {
                reclaimer = ca;
                reclaimerIsNew = false;
            }
            reclaimer.sendJson(Map.of(
                    "type", "resume", "resumeToken", ma.path("resumeToken").asText()));
            JsonNode joined = reclaimer.await(m -> m.path("type").asText().equals("joined"));
            assertEquals(ma.path("roomId").asText(), joined.path("roomId").asText());

            ca.close();
            cb.close();
            if (reclaimerIsNew) {
                reclaimer.close();
            }
        } finally {
            a.stop();
            b.stop();
        }
    }

    @Test
    void the_queue_survives_a_pod_death_and_a_returning_player_matches() throws Exception {
        EmbeddedServer a = instance("pod-A");
        EmbeddedServer b = instance("pod-B");
        try {
            // Alice enqueues on A (alone, size 2 → waiting), then A dies.
            TestGameClient aliceOnA = connect(a);
            aliceOnA.sendJson(Map.of("type", "enqueueMatchmaking", "name", "Alice", "size", 2));
            Thread.sleep(1_200); // let a tick elapse: the entry is in Redis
            aliceOnA.close();
            a.stop();

            // Let Alice's orphaned entry (homePod pod-A, dead) expire via its TTL (3s) before
            // re-enqueuing: otherwise the leader could pair it and "steal" the returning player's match.
            Thread.sleep(3_500);

            // Alice returns via the survivor B and re-enqueues; Bob enqueues on B too.
            TestGameClient aliceOnB = connect(b);
            aliceOnB.sendJson(Map.of("type", "enqueueMatchmaking", "name", "Alice", "size", 2));
            TestGameClient bobOnB = connect(b);
            bobOnB.sendJson(Map.of("type", "enqueueMatchmaking", "name", "Bob", "size", 2));

            JsonNode ma = aliceOnB.await(m -> m.path("type").asText().equals("matched"));
            JsonNode mb = bobOnB.await(m -> m.path("type").asText().equals("matched"));
            assertEquals(
                    ma.path("roomId").asText(),
                    mb.path("roomId").asText(),
                    "matchmaking keeps working after a pod loss; a returning player matches via the survivor");

            aliceOnB.close();
            bobOnB.close();
        } finally {
            b.stop();
        }
    }
}
