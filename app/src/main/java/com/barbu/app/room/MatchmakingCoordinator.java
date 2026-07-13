package com.barbu.app.room;

import com.barbu.app.cluster.MatchmakingQueue;
import com.barbu.app.cluster.MatchmakingQueue.Assignment;
import com.barbu.app.cluster.MatchmakingQueue.Entry;
import com.barbu.app.cluster.RoomRegistry;
import com.barbu.app.rating.EloConfig;
import com.barbu.app.rating.RatingService;
import com.barbu.engine.variant.Variants;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Value;
import io.micronaut.websocket.WebSocketSession;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Autorité de matchmaking multi-pod. Chaque tick : (1) livre à ses sockets locales les assignations
 * écrites par le leader (push {@code matched} → le client fait {@code resume} et réclame son siège
 * réservé sur le pod propriétaire) ; (2) s'il détient le lease leader, lit la file partagée, décide
 * via la logique pure ({@link CasualMatchmaker}/{@link RankedMatchmaker}) et forme les tables.
 *
 * {@code @Context} (bean eager) : le ticker doit tourner dès le démarrage, indépendamment de toute
 * connexion locale — un pod peut être leader et devoir former des tables pour les entrées d'AUTRES pods.
 */
@Context
public class MatchmakingCoordinator {

    private static final String LEADER_KEY = "barbu:mm:leader";

    private final MatchmakingQueue queue;
    private final RoomManager rooms;
    private final RoomRegistry registry;
    private final RatingService ratingService;
    private final EloConfig eloConfig;
    private final ObjectMapper mapper;
    private final String podId;
    private final long fillTimeoutMs;
    private final long entryTtlMs;
    private final long assignTtlMs;
    private final long leaderLeaseMs;

    private final Map<String, WebSocketSession> localSessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public MatchmakingCoordinator(
            MatchmakingQueue queue,
            RoomManager rooms,
            RoomRegistry registry,
            RatingService ratingService,
            EloConfig eloConfig,
            ObjectMapper mapper,
            @Value("${POD_ID:local}") String podId,
            @Value("${barbu.mm.fill-timeout-ms:8000}") long fillTimeoutMs,
            @Value("${barbu.mm.entry-ttl-ms:5000}") long entryTtlMs,
            @Value("${barbu.mm.assign-ttl-ms:15000}") long assignTtlMs,
            @Value("${barbu.mm.leader-lease-ms:3000}") long leaderLeaseMs) {
        this.queue = queue;
        this.rooms = rooms;
        this.registry = registry;
        this.ratingService = ratingService;
        this.eloConfig = eloConfig;
        this.mapper = mapper;
        this.podId = podId;
        this.fillTimeoutMs = fillTimeoutMs;
        this.entryTtlMs = entryTtlMs;
        this.assignTtlMs = assignTtlMs;
        this.leaderLeaseMs = leaderLeaseMs;
    }

    @PostConstruct
    void start() {
        scheduler.scheduleAtFixedRate(this::tick, 1, 1, TimeUnit.SECONDS);
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }

    public synchronized void enqueueCasual(WebSocketSession session, String name, int desiredSize) {
        int size = Math.clamp(desiredSize, 2, 10);
        if (alreadyLocallyQueued(session)) {
            return;
        }
        Long userId = session.get("userId", Long.class).orElse(null);
        register(session, new Entry(newEntryId(session), userId, name, null, size, podId, now()));
    }

    public synchronized void enqueueRanked(WebSocketSession session, String name) {
        Long userId = session.get("userId", Long.class).orElse(null);
        if (userId == null) {
            return; // ranked exige un compte ; le WS a déjà renvoyé l'erreur
        }
        if (alreadyLocallyQueued(session)) {
            return;
        }
        // Idempotent par compte à l'échelle du cluster : un second onglet/pod ne crée pas un 2e siège.
        for (Entry e : queue.ranked()) {
            if (userId.equals(e.userId())) {
                return;
            }
        }
        register(
                session, new Entry(newEntryId(session), userId, name, ratingService.ratingOf(userId), 0, podId, now()));
    }

    public synchronized void cancel(WebSocketSession session) {
        session.get("mmEntry", String.class).ifPresent(entryId -> {
            queue.remove(entryId);
            localSessions.remove(entryId);
        });
        session.remove("mmEntry");
    }

    private boolean alreadyLocallyQueued(WebSocketSession session) {
        String entryId = session.get("mmEntry", String.class).orElse(null);
        return entryId != null && localSessions.containsKey(entryId);
    }

    private String newEntryId(WebSocketSession session) {
        String entryId = UUID.randomUUID().toString();
        session.put("mmEntry", entryId);
        return entryId;
    }

    private void register(WebSocketSession session, Entry entry) {
        localSessions.put(entry.entryId(), session);
        queue.add(entry, entryTtlMs);
    }

    void tick() {
        deliverLocal();
        renewLocal();
        if (registry.tryClaim(LEADER_KEY, podId, leaderLeaseMs)) {
            decideCasual();
            decideRanked();
        }
    }

    private void deliverLocal() {
        for (String entryId : List.copyOf(localSessions.keySet())) {
            Optional<Assignment> assignment = queue.takeAssignment(entryId);
            if (assignment.isPresent()) {
                WebSocketSession session = localSessions.remove(entryId);
                Assignment a = assignment.get();
                if (session != null) {
                    send(
                            session,
                            Map.of(
                                    "type", "matched",
                                    "roomId", a.roomId(),
                                    "pod", a.ownerPod(),
                                    "resumeToken", a.resumeToken()));
                }
            }
        }
    }

    private void renewLocal() {
        for (String entryId : localSessions.keySet()) {
            queue.renew(entryId, entryTtlMs);
        }
    }

    private void decideCasual() {
        for (int size = 2; size <= 10; size++) {
            List<Entry> waiting = queue.casual(size);
            if (waiting.isEmpty()) {
                continue;
            }
            final int seats = size;
            List<CasualMatchmaker.Candidate> candidates = waiting.stream()
                    .map(e -> new CasualMatchmaker.Candidate(e.enqueuedAt()))
                    .toList();
            CasualMatchmaker.decideFormation(candidates, now(), fillTimeoutMs, seats)
                    .ifPresent(f -> form(seats, "casual", waiting, f.indices(), f.botsToAdd()));
        }
    }

    private void decideRanked() {
        List<Entry> waiting = queue.ranked();
        if (waiting.isEmpty()) {
            return;
        }
        List<RankedMatchmaker.Candidate> candidates = waiting.stream()
                .map(e -> new RankedMatchmaker.Candidate(e.rating(), e.enqueuedAt()))
                .toList();
        RankedMatchmaker.decideFormation(candidates, now(), eloConfig)
                .ifPresent(f -> form(eloConfig.rankedTableSize(), "ranked", waiting, f.indices(), f.botsToAdd()));
    }

    private void form(int size, String mode, List<Entry> waiting, List<Integer> indices, int botsToAdd) {
        List<Entry> seated = indices.stream().map(waiting::get).toList();
        for (Entry e : seated) {
            queue.remove(e.entryId()); // retire de la file avant d'asseoir : pas de double-match
        }
        GameRoom room = rooms.create(size, Variants.DEVELOPER, mode);
        for (Entry e : seated) {
            String token = room.reserveHuman(e.name(), e.userId());
            queue.assign(e.entryId(), new Assignment(room.id(), token, podId), assignTtlMs);
        }
        for (int b = 0; b < botsToAdd; b++) {
            room.addBot();
        }
        room.start(rooms.newSeed());
    }

    private long now() {
        return System.currentTimeMillis();
    }

    private void send(WebSocketSession session, Map<String, Object> payload) {
        try {
            session.sendSync(mapper.writeValueAsString(payload));
        } catch (Exception ignored) {
            // client parti ; l'assignation a déjà été consommée, le siège réservé bot-fill au besoin
        }
    }
}
