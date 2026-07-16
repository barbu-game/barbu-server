package com.barbu.app.cluster;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Single-pod snapshot store (default, when {@code redis.uri} is absent). */
@Singleton
@Requires(missingProperty = "redis.uri")
public class InMemorySnapshotStore implements SnapshotStore {

    private final Map<String, String> snapshots = new ConcurrentHashMap<>();

    @Override
    public void save(String roomId, String json) {
        snapshots.put(roomId, json);
    }

    @Override
    public Optional<String> load(String roomId) {
        return Optional.ofNullable(snapshots.get(roomId));
    }

    @Override
    public void delete(String roomId) {
        snapshots.remove(roomId);
    }
}
