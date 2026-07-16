package com.barbu.app.cluster;

import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import java.util.Optional;

/** Snapshot store shared across pods (active as soon as {@code redis.uri} is provided). */
@Singleton
@Requires(property = "redis.uri")
public class RedisSnapshotStore implements SnapshotStore {

    private static final String KEY = "barbu:room:snapshot:";

    private final RedisCommands<String, String> redis;

    public RedisSnapshotStore(StatefulRedisConnection<String, String> connection) {
        this.redis = connection.sync();
    }

    @Override
    public void save(String roomId, String json) {
        redis.set(KEY + roomId, json);
    }

    @Override
    public Optional<String> load(String roomId) {
        return Optional.ofNullable(redis.get(KEY + roomId));
    }

    @Override
    public void delete(String roomId) {
        redis.del(KEY + roomId);
    }
}
