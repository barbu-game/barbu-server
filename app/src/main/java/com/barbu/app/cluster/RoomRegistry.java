package com.barbu.app.cluster;

import java.util.Optional;

/** Exclusive, durable ownership of a table by a pod, via a TTL lease. */
public interface RoomRegistry {

    /** Becomes (or stays) owner of {@code roomId} for {@code ttlMs}. */
    boolean tryClaim(String roomId, String podId, long ttlMs);

    /** Extends our lease; false if we no longer own the table. */
    boolean renew(String roomId, String podId, long ttlMs);

    /** Releases ownership, only if we still hold it. */
    void release(String roomId, String podId);

    /** Current owning pod, if any. */
    Optional<String> ownerOf(String roomId);
}
