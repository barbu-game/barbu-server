package com.barbu.app.chaos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.ClientWebSocket;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/** Client WebSocket headless : envoie des commandes JSON et collecte les messages reçus. */
@ClientWebSocket("/ws/game")
public class TestGameClient implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CopyOnWriteArrayList<JsonNode> messages = new CopyOnWriteArrayList<>();
    private WebSocketSession session;

    @OnOpen
    void onOpen(WebSocketSession session) {
        this.session = session;
    }

    @OnMessage
    void onMessage(String message) throws Exception {
        messages.add(MAPPER.readTree(message));
    }

    void sendJson(Object payload) {
        try {
            session.sendSync(MAPPER.writeValueAsString(payload));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Attend (≤10s) un message satisfaisant le prédicat et le renvoie. */
    JsonNode await(Predicate<JsonNode> match) throws InterruptedException {
        for (int i = 0; i < 200; i++) {
            for (JsonNode m : messages) {
                if (match.test(m)) {
                    return m;
                }
            }
            Thread.sleep(50);
        }
        throw new AssertionError("no message matched within timeout; got " + messages);
    }

    @Override
    public void close() {
        if (session != null && session.isOpen()) {
            session.close();
        }
    }
}
