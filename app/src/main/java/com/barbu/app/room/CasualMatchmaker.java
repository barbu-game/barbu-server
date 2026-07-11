package com.barbu.app.room;

import com.barbu.engine.variant.Variants;
import io.micronaut.websocket.WebSocketSession;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Casual matchmaking: queue players by desired table size, match as soon as enough
 * humans are waiting, otherwise fill the table with bots after a short timeout. Unranked.
 */
@Singleton
public class CasualMatchmaker implements Matchmaker {

    private static final long FILL_TIMEOUT_MS = 8000;

    private final RoomManager rooms;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Map<Integer, Deque<Waiting>> queues = new HashMap<>();
    private final Map<Integer, ScheduledFuture<?>> timeouts = new HashMap<>();

    public CasualMatchmaker(RoomManager rooms) {
        this.rooms = rooms;
    }

    private record Waiting(WebSocketSession session, String name) {}

    public synchronized void enqueue(WebSocketSession session, String name, int desiredSize) {
        int size = Math.clamp(desiredSize, 2, 10);
        session.put("mmSize", size);
        queues.computeIfAbsent(size, k -> new ArrayDeque<>()).add(new Waiting(session, name));

        if (queues.get(size).size() >= size) {
            formMatch(size, size);
        } else {
            scheduleFill(size);
        }
    }

    public synchronized void cancel(WebSocketSession session) {
        Integer size = session.get("mmSize", Integer.class).orElse(null);
        if (size == null) {
            return;
        }
        Deque<Waiting> queue = queues.get(size);
        if (queue != null) {
            queue.removeIf(w -> w.session() == session);
        }
    }

    public synchronized int queuedCount() {
        return queues.values().stream().mapToInt(java.util.Deque::size).sum();
    }

    private void scheduleFill(int size) {
        timeouts.computeIfAbsent(
                size, s -> scheduler.schedule(() -> fillWithBots(s), FILL_TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }

    private synchronized void fillWithBots(int size) {
        timeouts.remove(size);
        Deque<Waiting> queue = queues.get(size);
        if (queue != null && !queue.isEmpty()) {
            formMatch(size, queue.size());
        }
    }

    private void formMatch(int size, int humansToTake) {
        Deque<Waiting> queue = queues.get(size);
        List<Waiting> taken = new ArrayList<>();
        for (int i = 0; i < humansToTake && !queue.isEmpty(); i++) {
            taken.add(queue.poll());
        }
        if (taken.isEmpty()) {
            return;
        }
        ScheduledFuture<?> pending = timeouts.remove(size);
        if (pending != null) {
            pending.cancel(false);
        }

        GameRoom room = rooms.create(size, Variants.DEVELOPER, "casual");
        for (Waiting w : taken) {
            String name = w.session().get("username", String.class).orElse(w.name());
            Long userId = w.session().get("userId", Long.class).orElse(null);
            int seat = room.addHuman(w.session(), name, userId);
            w.session().put("roomId", room.id());
            w.session().put("seat", seat);
        }
        while (room.addBot()) {}
        room.start(rooms.newSeed());
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }
}
