package com.barbu.app.chaos;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.websocket.WebSocketClient;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

/** Sur un seul pod : deux joueurs casual (taille 2) forment UNE table et reçoivent matched→joined. */
class MatchmakingIntegrationTest {

    private TestGameClient connect(EmbeddedServer server) {
        WebSocketClient client = server.getApplicationContext().createBean(WebSocketClient.class, server.getURL());
        return Flux.from(client.connect(TestGameClient.class, "/ws/game")).blockFirst();
    }

    @Test
    void two_casual_players_of_the_same_size_form_one_table() throws Exception {
        // bot-delay élevé pour garder la table stable après le start
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
