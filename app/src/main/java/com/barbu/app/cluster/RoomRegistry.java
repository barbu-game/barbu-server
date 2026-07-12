package com.barbu.app.cluster;

import java.util.Optional;

/** Propriété exclusive et durable d'une table par un pod, via lease à TTL. */
public interface RoomRegistry {

    /** Devient (ou reste) propriétaire de {@code roomId} pour {@code ttlMs}. */
    boolean tryClaim(String roomId, String podId, long ttlMs);

    /** Prolonge notre lease ; faux si on ne possède plus la table. */
    boolean renew(String roomId, String podId, long ttlMs);

    /** Relâche la propriété, seulement si on la détient encore. */
    void release(String roomId, String podId);

    /** Pod propriétaire courant, s'il existe. */
    Optional<String> ownerOf(String roomId);
}
