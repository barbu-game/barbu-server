package com.barbu.app.cluster;

import java.util.List;
import java.util.Optional;

/**
 * Matchmaking queue shared across pods. In-memory impl (single-pod) or Redis (multi-pod), depending
 * on the presence of {@code redis.uri}. Sockets stay pod-local: only this metadata travels.
 */
public interface MatchmakingQueue {

    /** {@code rating != null} ⇒ ranked entry; {@code rating == null} ⇒ casual (routed by {@code desiredSize}). */
    record Entry(
            String entryId,
            Long userId,
            String name,
            Integer rating,
            int desiredSize,
            String homePod,
            long enqueuedAt) {}

    record Assignment(String roomId, String resumeToken, String ownerPod) {}

    void add(Entry e, long ttlMs);

    void remove(String entryId);

    /** Extends the TTL of an entry still in the queue (home pod heartbeat). */
    void renew(String entryId, long ttlMs);

    List<Entry> casual(int size);

    List<Entry> ranked();

    void assign(String entryId, Assignment a, long ttlMs);

    /** Reads and deletes the assignment (once-only delivery). */
    Optional<Assignment> takeAssignment(String entryId);

    int size();
}
