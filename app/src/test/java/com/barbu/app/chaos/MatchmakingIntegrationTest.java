package com.barbu.app.chaos;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.websocket.WebSocketClient;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

/** On a single pod: two casual players (size 2) form ONE table and receive matched→joined. */
class MatchmakingIntegrationTest {

    private TestGameClient connect(EmbeddedServer server) {
        WebSocketClient client = server.getApplicationContext().createBean(WebSocketClient.class, server.getURL());
        return Flux.from(client.connect(TestGameClient.class, "/ws/game")).blockFirst();
    }

    @Test
    void two_casual_players_of_the_same_size_form_one_table() throws Exception {
        // high bot-delay to keep the table stable after start
        EmbeddedServer server = ApplicationContext.run(
                EmbeddedServer.class, Map.of("POD_ID", "local", "barbu.bot-delay-ms", 3_600_000));
        try {
            TestGameClient a = connect(server);
            TestGameClient b = connect(server);
            a.sendJson(Map.of("type", "enqueueMatchmaking", "name", "Alice", "size", 2));
            b.sendJson(Map.of("type", "enqueueMatchmaking", "name", "Bob", "size", 2));

            JsonNode matchedA = a.await(m -> m.path("type").asText().equals("matched"));
            JsonNode matchedB = b.await(m -> m.path("type").asText().equals("matched"));
            assertEquals(
                    matchedA.path("roomId").asText(),
                    matchedB.path("roomId").asText(),
                    "both are matched into the same room");
            // Table reserved on this same pod: no `pod` field, so no /pod/<pod> redirect (the prefix
            // only exists behind Traefik in multi-pod). The client reclaims its seat in place — this is
            // what breaks browser matchmaking on a single instance when `pod` is emitted.
            assertFalse(matchedA.has("pod"), "same-pod match must not ask the client to switch pods");

            a.sendJson(Map.of(
                    "type",
                    "resume",
                    "resumeToken",
                    matchedA.path("resumeToken").asText()));
            JsonNode joinedA = a.await(m -> m.path("type").asText().equals("joined"));
            assertEquals(
                    matchedA.path("roomId").asText(), joinedA.path("roomId").asText());

            a.close();
            b.close();
        } finally {
            server.stop();
        }
    }
}
