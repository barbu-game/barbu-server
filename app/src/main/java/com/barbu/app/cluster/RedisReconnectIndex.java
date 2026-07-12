package com.barbu.app.cluster;

import com.barbu.app.room.ReconnectIndex;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

/** Index de reconnexion partagé entre pods (actif dès que {@code redis.uri} est fourni). */
@Singleton
@Requires(property = "redis.uri")
public class RedisReconnectIndex implements ReconnectIndex {

    private static final String USER = "barbu:reconnect:user:";
    private static final String TOKEN = "barbu:reconnect:token:";

    private final RedisCommands<String, String> redis;

    public RedisReconnectIndex(StatefulRedisConnection<String, String> connection) {
        this.redis = connection.sync();
    }

    @Override
    public void register(Long userId, String token, String roomId) {
        if (userId != null) {
            redis.set(USER + userId, roomId);
        }
        if (token != null) {
            redis.set(TOKEN + token, roomId);
        }
    }

    @Override
    public String roomForUser(long userId) {
        return redis.get(USER + userId);
    }

    @Override
    public String roomForToken(String token) {
        return token == null ? null : redis.get(TOKEN + token);
    }

    @Override
    public void forget(Long userId, String token, String roomId) {
        // Suppression conditionnelle : l'entrée peut déjà pointer sur une autre room.
        if (userId != null && roomId.equals(redis.get(USER + userId))) {
            redis.del(USER + userId);
        }
        if (token != null && roomId.equals(redis.get(TOKEN + token))) {
            redis.del(TOKEN + token);
        }
    }
}
