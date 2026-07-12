package com.barbu.app.cluster;

import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import java.util.Optional;

/** Lease de propriété partagé entre pods (actif dès que {@code redis.uri} est fourni). */
@Singleton
@Requires(property = "redis.uri")
public class RedisRoomRegistry implements RoomRegistry {

    private static final String KEY = "barbu:room:owner:";
    // Renew/release « seulement si je suis le propriétaire » — atomiques côté Redis.
    private static final String RENEW =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('pexpire', KEYS[1], ARGV[2]) else return 0 end";
    private static final String RELEASE =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

    private final RedisCommands<String, String> redis;

    public RedisRoomRegistry(StatefulRedisConnection<String, String> connection) {
        this.redis = connection.sync();
    }

    @Override
    public boolean tryClaim(String roomId, String podId, long ttlMs) {
        String key = KEY + roomId;
        if (podId.equals(redis.get(key))) {
            redis.pexpire(key, ttlMs);
            return true;
        }
        return "OK".equals(redis.set(key, podId, SetArgs.Builder.nx().px(ttlMs)));
    }

    @Override
    public boolean renew(String roomId, String podId, long ttlMs) {
        Long r = redis.eval(RENEW, ScriptOutputType.INTEGER, new String[] {KEY + roomId}, podId, String.valueOf(ttlMs));
        return r != null && r == 1L;
    }

    @Override
    public void release(String roomId, String podId) {
        redis.eval(RELEASE, ScriptOutputType.INTEGER, new String[] {KEY + roomId}, podId);
    }

    @Override
    public Optional<String> ownerOf(String roomId) {
        return Optional.ofNullable(redis.get(KEY + roomId));
    }
}
