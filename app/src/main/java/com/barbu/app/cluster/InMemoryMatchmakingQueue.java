package com.barbu.app.cluster;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/** Single-pod matchmaking queue (default, when {@code redis.uri} is absent). */
@Singleton
@Requires(missingProperty = "redis.uri")
public class InMemoryMatchmakingQueue implements MatchmakingQueue {

    private record Held(Entry entry, long expiresAt) {}

    private record Assigned(Assignment assignment, long expiresAt) {}

    private final Map<String, Held> entries = new ConcurrentHashMap<>();
    private final Map<String, Assigned> assignments = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    public InMemoryMatchmakingQueue() {
        this(System::currentTimeMillis);
    }

    InMemoryMatchmakingQueue(LongSupplier clock) {
        this.clock = clock;
    }

    @Override
    public void add(Entry e, long ttlMs) {
        entries.put(e.entryId(), new Held(e, clock.getAsLong() + ttlMs));
    }

    @Override
    public void remove(String entryId) {
        entries.remove(entryId);
    }

    @Override
    public void renew(String entryId, long ttlMs) {
        Held held = entries.get(entryId);
        if (held != null) {
            entries.put(entryId, new Held(held.entry(), clock.getAsLong() + ttlMs));
        }
    }

    @Override
    public List<Entry> casual(int size) {
        return live(e -> e.rating() == null && e.desiredSize() == size);
    }

    @Override
    public List<Entry> ranked() {
        return live(e -> e.rating() != null);
    }

    private List<Entry> live(Predicate<Entry> filter) {
        long now = clock.getAsLong();
        entries.entrySet().removeIf(en -> en.getValue().expiresAt() <= now);
        List<Entry> out = new ArrayList<>();
        for (Held h : entries.values()) {
            if (filter.test(h.entry())) {
                out.add(h.entry());
            }
        }
        out.sort(Comparator.comparingLong(Entry::enqueuedAt));
        return out;
    }

    @Override
    public void assign(String entryId, Assignment a, long ttlMs) {
        assignments.put(entryId, new Assigned(a, clock.getAsLong() + ttlMs));
    }

    @Override
    public Optional<Assignment> takeAssignment(String entryId) {
        Assigned a = assignments.remove(entryId);
        if (a == null || a.expiresAt() <= clock.getAsLong()) {
            return Optional.empty();
        }
        return Optional.of(a.assignment());
    }

    @Override
    public int size() {
        long now = clock.getAsLong();
        entries.entrySet().removeIf(en -> en.getValue().expiresAt() <= now);
        return entries.size();
    }
}
