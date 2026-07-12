package com.barbu.app.room;

import com.barbu.app.cluster.RoomRegistry;
import com.barbu.app.cluster.SnapshotStore;
import com.barbu.app.persistence.MatchRecorder;
import com.barbu.app.rating.RatingService;
import com.barbu.engine.variant.Variant;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.annotation.Value;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Singleton
public class RoomManager {

    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();
    private final ObjectMapper mapper;
    private final MatchRecorder recorder;
    private final com.barbu.app.metrics.GameMetrics metrics;
    private final RatingService ratingService;
    private final ReconnectIndex reconnectIndex;
    private final long botDelayMs;
    private final long turnTimeoutMs;
    private final int turnTimeoutStrikes;
    private final long roomGraceMs;
    private final RoomRegistry registry;
    private final SnapshotStore snapshots;
    private final SnapshotCodec codec;
    private final String podId;
    private static final long LEASE_TTL_MS = 15_000;
    private static final long RENEW_PERIOD_MS = 5_000;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Random random = new Random();

    public RoomManager(
            ObjectMapper mapper,
            MatchRecorder recorder,
            com.barbu.app.metrics.GameMetrics metrics,
            RatingService ratingService,
            ReconnectIndex reconnectIndex,
            RoomRegistry registry,
            SnapshotStore snapshots,
            SnapshotCodec codec,
            @Value("${POD_ID:local}") String podId,
            @Value("${barbu.bot-delay-ms:650}") long botDelayMs,
            @Value("${barbu.turn-timeout-ms:60000}") long turnTimeoutMs,
            @Value("${barbu.turn-timeout-strikes:2}") int turnTimeoutStrikes,
            @Value("${barbu.room-grace-ms:90000}") long roomGraceMs) {
        this.mapper = mapper;
        this.recorder = recorder;
        this.metrics = metrics;
        this.ratingService = ratingService;
        this.reconnectIndex = reconnectIndex;
        this.registry = registry;
        this.snapshots = snapshots;
        this.codec = codec;
        this.podId = podId;
        this.botDelayMs = botDelayMs;
        this.turnTimeoutMs = turnTimeoutMs;
        this.turnTimeoutStrikes = turnTimeoutStrikes;
        this.roomGraceMs = roomGraceMs;
        scheduler.scheduleAtFixedRate(this::renewLeases, RENEW_PERIOD_MS, RENEW_PERIOD_MS, TimeUnit.MILLISECONDS);
    }

    public GameRoom create(int requestedPlayerCount, Variant variant) {
        return create(requestedPlayerCount, variant, "private");
    }

    public GameRoom create(int requestedPlayerCount, Variant variant, String mode) {
        int playerCount = Math.clamp(requestedPlayerCount, 2, 10);
        String id = newCode();
        GameRoom room = new GameRoom(
                id,
                playerCount,
                variant,
                mapper,
                scheduler,
                botDelayMs,
                recorder,
                metrics,
                mode,
                ratingService,
                reconnectIndex,
                turnTimeoutMs,
                turnTimeoutStrikes);
        rooms.put(id, room);
        registry.tryClaim(id, podId, LEASE_TTL_MS);
        room.setOnStateChanged(() -> persist(room));
        persist(room);
        return room;
    }

    /** Écrit le snapshot durable et prolonge notre lease : appelé à chaque changement d'état de la table. */
    public void persist(GameRoom room) {
        snapshots.save(room.id(), codec.encode(room.snapshot()));
        registry.renew(room.id(), podId, LEASE_TTL_MS);
    }

    private void renewLeases() {
        for (String id : rooms.keySet()) {
            registry.renew(id, podId, LEASE_TTL_MS);
        }
    }

    /** Relâche immédiatement tous les leases possédés (drain) : un survivant peut réclamer sans attendre le TTL. */
    public void releaseAllLeases() {
        for (String id : rooms.keySet()) {
            registry.release(id, podId);
        }
    }

    public GameRoom get(String id) {
        return id == null ? null : rooms.get(id);
    }

    public GameRoom roomForUser(long userId) {
        return get(reconnectIndex.roomForUser(userId));
    }

    public GameRoom roomForToken(String token) {
        return get(reconnectIndex.roomForToken(token));
    }

    public String roomIdForToken(String token) {
        return reconnectIndex.roomForToken(token);
    }

    public String roomIdForUser(long userId) {
        return reconnectIndex.roomForUser(userId);
    }

    public enum Resolution {
        LOCAL,
        REDIRECT,
        NONE
    }

    public record Resolved(Resolution resolution, GameRoom room, String ownerPod) {}

    /**
     * Résout où vit une table. Possédée localement → LOCAL. Sinon possédée par un autre pod vivant →
     * REDIRECT vers lui. Sinon, si un snapshot durable existe → on la réclame et on la réhydrate
     * localement (self-healing sur perte de pod). Sinon → NONE.
     */
    public Resolved resolveOrRehydrate(String roomId) {
        GameRoom local = rooms.get(roomId);
        if (local != null) {
            return new Resolved(Resolution.LOCAL, local, podId);
        }
        Optional<String> owner = registry.ownerOf(roomId);
        if (owner.isPresent() && !owner.get().equals(podId)) {
            return new Resolved(Resolution.REDIRECT, null, owner.get());
        }
        Optional<String> json = snapshots.load(roomId);
        if (json.isEmpty()) {
            return new Resolved(Resolution.NONE, null, null);
        }
        if (!registry.tryClaim(roomId, podId, LEASE_TTL_MS)) {
            return new Resolved(
                    Resolution.REDIRECT, null, registry.ownerOf(roomId).orElse(null));
        }
        GameRoom room = GameRoom.fromSnapshot(
                codec.decode(json.get()),
                mapper,
                scheduler,
                botDelayMs,
                recorder,
                metrics,
                ratingService,
                reconnectIndex,
                turnTimeoutMs,
                turnTimeoutStrikes);
        room.setOnStateChanged(() -> persist(room));
        rooms.put(roomId, room);
        return new Resolved(Resolution.LOCAL, room, podId);
    }

    public void remove(String id) {
        GameRoom room = rooms.remove(id);
        if (room != null) {
            room.cancelPendingTeardown();
            room.clearReconnectEntries(reconnectIndex);
        }
        snapshots.delete(id);
        registry.release(id, podId);
    }

    /**
     * The last human just dropped from this room: defer teardown by the grace window so a backgrounded
     * host or an invited joiner can return to the same code. Teardown only fires if the room is still
     * empty of humans when the window closes; any reconnect/join cancels it.
     */
    public void scheduleTeardown(String id) {
        GameRoom room = get(id);
        if (room == null) {
            return;
        }
        room.scheduleTeardown(roomGraceMs, () -> {
            room.recordAbandonmentForfeit();
            remove(id);
        });
    }

    public int activeRoomCount() {
        return rooms.size();
    }

    public int activeHumanCount() {
        return rooms.values().stream().mapToInt(GameRoom::connectedHumanCount).sum();
    }

    public int activeBotCount() {
        return rooms.values().stream().mapToInt(GameRoom::botCount).sum();
    }

    public long newSeed() {
        return random.nextLong();
    }

    private String newCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder(5);
            for (int i = 0; i < 5; i++) {
                sb.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
            }
            code = sb.toString();
        } while (rooms.containsKey(code));
        return code;
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }
}
