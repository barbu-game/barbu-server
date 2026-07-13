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
 * Prouve les deux gains vs docker-compose : (1) défragmentation — deux joueurs sur des pods
 * DIFFÉRENTS forment UNE table via la file Redis partagée ; (2) durabilité — la file survit à la
 * mort d'un pod, un joueur qui revient re-enfile et matche via le pod survivant.
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
        // TTL d'entrée court : après la mort d'un pod, l'entrée orpheline de son joueur en file
        // expire vite, pour que le test de durabilité soit déterministe (pas de match « volé »).
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

            // Le client se branche sur le pod propriétaire et réclame son siège réservé.
            String ownerPod = ma.path("pod").asText();
            EmbeddedServer owner = serverForPod(ownerPod, a, "pod-A", b, "pod-B");
            assertNotNull(owner, "owner pod is one of the two instances");
            TestGameClient onOwner = connect(owner);
            onOwner.sendJson(Map.of(
                    "type", "resume", "resumeToken", ma.path("resumeToken").asText()));
            JsonNode joined = onOwner.await(m -> m.path("type").asText().equals("joined"));
            assertEquals(ma.path("roomId").asText(), joined.path("roomId").asText());

            ca.close();
            cb.close();
            onOwner.close();
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
            // Alice enfile sur A (seule, taille 2 → en attente), puis A meurt.
            TestGameClient aliceOnA = connect(a);
            aliceOnA.sendJson(Map.of("type", "enqueueMatchmaking", "name", "Alice", "size", 2));
            Thread.sleep(1_200); // laisse un tick s'écouler : l'entrée est dans Redis
            aliceOnA.close();
            a.stop();

            // Laisse l'entrée orpheline d'Alice (homePod pod-A, mort) expirer via son TTL (3s) avant de
            // re-enfiler : sinon le leader pourrait l'apparier et « voler » le match du joueur revenu.
            Thread.sleep(3_500);

            // Alice revient via le survivant B et re-enfile ; Bob enfile aussi sur B.
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
