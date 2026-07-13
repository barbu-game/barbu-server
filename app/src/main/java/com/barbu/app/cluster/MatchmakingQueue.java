package com.barbu.app.cluster;

import java.util.List;
import java.util.Optional;

/**
 * File de matchmaking partagée entre pods. Impl mémoire (mono-pod) ou Redis (multi-pod), selon la
 * présence de {@code redis.uri}. Les sockets restent pod-locales : seules ces métadonnées transitent.
 */
public interface MatchmakingQueue {

    /** {@code rating != null} ⇒ entrée ranked ; {@code rating == null} ⇒ casual (routée par {@code desiredSize}). */
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

    /** Prolonge le TTL d'une entrée encore en file (heartbeat du pod home). */
    void renew(String entryId, long ttlMs);

    List<Entry> casual(int size);

    List<Entry> ranked();

    void assign(String entryId, Assignment a, long ttlMs);

    /** Lit et supprime l'assignation (livraison unique). */
    Optional<Assignment> takeAssignment(String entryId);

    int size();
}
