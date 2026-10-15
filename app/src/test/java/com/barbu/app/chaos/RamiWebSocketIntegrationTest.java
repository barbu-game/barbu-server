package com.barbu.app.chaos;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.websocket.WebSocketClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

/** A Rami table over the real WebSocket: creation, a legal draw, and a rejected move. */
class RamiWebSocketIntegrationTest {

    private TestGameClient connect(EmbeddedServer server) {
        WebSocketClient client = server.getApplicationContext().createBean(WebSocketClient.class, server.getURL());
        return Flux.from(client.connect(TestGameClient.class, "/ws/game")).blockFirst();
    }

    private static boolean isState(JsonNode m) {
        return m.path("type").asText().equals("state");
    }

    @Test
    void create_play_and_reject_over_the_socket() throws Exception {
        EmbeddedServer server =
                ApplicationContext.run(EmbeddedServer.class, Map.of("POD_ID", "local", "barbu.bot-delay-ms", 1));
        try (TestGameClient host = connect(server)) {
            host.sendJson(Map.of("type", "createRoom", "gameType", "RAMI", "playerCount", 9, "name", "Alice"));
            JsonNode lobby =
                    host.await(m -> isState(m) && m.path("phase").asText().equals("LOBBY"));
            assertEquals("RAMI", lobby.path("gameType").asText());
            assertEquals(5, lobby.path("playerCount").asInt(), "player count is clamped to 4..5");

            for (int i = 0; i < 4; i++) {
                host.sendJson(Map.of("type", "addBot"));
            }
            host.sendJson(Map.of("type", "start"));

            JsonNode myTurn = host.await(m -> isState(m)
                    && m.path("currentActor").asInt(-1) == 0
                    && m.path("rami").path("turnPhase").asText().equals("DRAW"));
            int handSize = myTurn.path("rami").path("yourHand").size();

            host.sendJson(Map.of("type", "play", "move", Map.of("kind", "draw", "source", "STOCK")));
            host.await(m -> isState(m)
                    && m.path("rami").path("turnPhase").asText().equals("PLAY")
                    && m.path("rami").path("yourHand").size() == handSize + 1);

            host.sendJson(Map.of("type", "play", "move", Map.of("kind", "discard", "cardId", 999)));
            JsonNode error = host.await(m -> m.path("type").asText().equals("error"));
            assertTrue(error.path("message").asText().startsWith("illegal move"));

            host.sendJson(Map.of("type", "play", "move", List.of("not", "a", "map")));
            host.await(m -> m.path("type").asText().equals("error")
                    && m.path("message").asText().contains("illegal move")
                    && m != error);
        } finally {
            server.stop();
        }
    }
}
