package com.barbu.app.room;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Single-pod reconnection index (the default, in the absence of {@code redis.uri}). */
@Singleton
@Requires(missingProperty = "redis.uri")
public class InMemoryReconnectIndex implements ReconnectIndex {

    private final Map<Long, String> roomByUser = new ConcurrentHashMap<>();
    private final Map<String, String> roomByToken = new ConcurrentHashMap<>();

    @Override
    public void register(Long userId, String token, String roomId) {
        if (userId != null) {
            roomByUser.put(userId, roomId);
        }
        if (token != null) {
            roomByToken.put(token, roomId);
        }
    }

    @Override
    public String roomForUser(long userId) {
        return roomByUser.get(userId);
    }

    @Override
    public String roomForToken(String token) {
        return token == null ? null : roomByToken.get(token);
    }

    @Override
    public void forget(Long userId, String token, String roomId) {
        if (userId != null) {
            roomByUser.remove(userId, roomId);
        }
        if (token != null) {
            roomByToken.remove(token, roomId);
        }
    }
}
