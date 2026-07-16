package com.barbu.app.cluster;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Single-pod ownership registry (default, when {@code redis.uri} is absent). */
@Singleton
@Requires(missingProperty = "redis.uri")
public class InMemoryRoomRegistry implements RoomRegistry {

    private record Lease(String podId, long expiresAtNanos) {}

    private final Map<String, Lease> leases = new ConcurrentHashMap<>();

    @Override
    public synchronized boolean tryClaim(String roomId, String podId, long ttlMs) {
        Lease current = leases.get(roomId);
        if (current != null && current.podId().equals(podId)) {
            leases.put(roomId, lease(podId, ttlMs));
            return true;
        }
        if (current != null && current.expiresAtNanos() > System.nanoTime()) {
            return false;
        }
        leases.put(roomId, lease(podId, ttlMs));
        return true;
    }

    @Override
    public synchronized boolean renew(String roomId, String podId, long ttlMs) {
        Lease current = leases.get(roomId);
        if (current == null || !current.podId().equals(podId)) {
            return false;
        }
        leases.put(roomId, lease(podId, ttlMs));
        return true;
    }

    @Override
    public synchronized void release(String roomId, String podId) {
        Lease current = leases.get(roomId);
        if (current != null && current.podId().equals(podId)) {
            leases.remove(roomId);
        }
    }

    @Override
    public synchronized Optional<String> ownerOf(String roomId) {
        Lease current = leases.get(roomId);
        if (current == null || current.expiresAtNanos() <= System.nanoTime()) {
            return Optional.empty();
        }
        return Optional.of(current.podId());
    }

    private Lease lease(String podId, long ttlMs) {
        return new Lease(podId, System.nanoTime() + ttlMs * 1_000_000L);
    }
}
