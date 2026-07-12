package com.barbu.app.cluster;

import java.util.Optional;

/** Persistance durable de l'état sérialisé d'une table, relocalisable entre pods. */
public interface SnapshotStore {

    void save(String roomId, String json);

    Optional<String> load(String roomId);

    void delete(String roomId);
}
