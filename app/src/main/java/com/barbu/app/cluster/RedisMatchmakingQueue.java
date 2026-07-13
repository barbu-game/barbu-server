package com.barbu.app.cluster;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** File de matchmaking partagée (active dès que {@code redis.uri} est fourni). */
@Singleton
@Requires(property = "redis.uri")
public class RedisMatchmakingQueue implements MatchmakingQueue {

    private static final String ENTRY = "barbu:mm:entry:";
    private static final String ASSIGNED = "barbu:mm:assigned:";
    private static final String RANKED = "barbu:mm:ranked";
    private static final String CASUAL = "barbu:mm:casual:";

    private final RedisCommands<String, String> redis;
    private final ObjectMapper mapper;

    public RedisMatchmakingQueue(StatefulRedisConnection<String, String> connection, ObjectMapper mapper) {
        this.redis = connection.sync();
        this.mapper = mapper;
    }

    private static String zsetKeyFor(Entry e) {
        return e.rating() != null ? RANKED : CASUAL + e.desiredSize();
    }

    private String toJson(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (Exception ex) {
            throw new IllegalStateException("matchmaking serialization failed", ex);
        }
    }

    private <T> T fromJson(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (Exception ex) {
            throw new IllegalStateException("matchmaking deserialization failed", ex);
        }
    }

    @Override
    public void add(Entry e, long ttlMs) {
        // L'entrée JSON porte un TTL ; le membre du ZSET (juste l'id) est nettoyé paresseusement à la
        // lecture si sa clé d'entrée a expiré. Le score = enqueuedAt donne l'ordre FIFO/ancienneté.
        redis.psetex(ENTRY + e.entryId(), ttlMs, toJson(e));
        redis.zadd(zsetKeyFor(e), (double) e.enqueuedAt(), e.entryId());
    }

    @Override
    public void remove(String entryId) {
        String json = redis.getdel(ENTRY + entryId);
        if (json != null) {
            Entry e = fromJson(json, Entry.class);
            redis.zrem(zsetKeyFor(e), entryId);
        }
    }

    @Override
    public void renew(String entryId, long ttlMs) {
        redis.pexpire(ENTRY + entryId, ttlMs);
    }

    @Override
    public List<Entry> casual(int size) {
        return readZset(CASUAL + size);
    }

    @Override
    public List<Entry> ranked() {
        return readZset(RANKED);
    }

    private List<Entry> readZset(String zsetKey) {
        List<String> ids = redis.zrange(zsetKey, 0, -1); // ascending by score = enqueuedAt
        List<Entry> out = new ArrayList<>(ids.size());
        for (String id : ids) {
            String json = redis.get(ENTRY + id);
            if (json == null) {
                redis.zrem(zsetKey, id); // entrée expirée → nettoyage paresseux du membre
            } else {
                out.add(fromJson(json, Entry.class));
            }
        }
        return out;
    }

    @Override
    public void assign(String entryId, Assignment a, long ttlMs) {
        redis.psetex(ASSIGNED + entryId, ttlMs, toJson(a));
    }

    @Override
    public Optional<Assignment> takeAssignment(String entryId) {
        String json = redis.getdel(ASSIGNED + entryId);
        return json == null ? Optional.empty() : Optional.of(fromJson(json, Assignment.class));
    }

    @Override
    public int size() {
        int total = ranked().size();
        for (int s = 2; s <= 10; s++) {
            total += casual(s).size();
        }
        return total;
    }
}
