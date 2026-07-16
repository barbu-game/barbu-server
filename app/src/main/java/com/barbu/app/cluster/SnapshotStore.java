package com.barbu.app.cluster;

import java.util.Optional;

/** Durable persistence of a table's serialized state, relocatable across pods. */
public interface SnapshotStore {

    void save(String roomId, String json);

    Optional<String> load(String roomId);

    void delete(String roomId);
}
