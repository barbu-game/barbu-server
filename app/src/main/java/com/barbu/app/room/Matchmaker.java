package com.barbu.app.room;

import io.micronaut.websocket.WebSocketSession;

/** File de matchmaking public. Implémentations en mémoire (swap Redis ultérieur). */
public interface Matchmaker {
    void enqueue(WebSocketSession session, String name, int desiredSize);

    void cancel(WebSocketSession session);

    int queuedCount();
}
