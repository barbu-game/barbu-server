package com.barbu.app.room;

import com.barbu.app.rating.EloConfig;
import com.barbu.app.rating.RatingService;
import com.barbu.engine.variant.Variants;
import io.micronaut.websocket.WebSocketSession;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * File ranked : taille de table fixe (config), fenêtre de rating qui s'élargit avec l'attente,
 * bot-fill borné aux tables faible-ELO après timeout. La décision de formation est pure et testée
 * en isolation ; le wrapper {@code @Singleton} l'habille avec les sessions et la création de room.
 */
@Singleton
public class RankedMatchmaker implements Matchmaker {

    /** Un joueur en attente, anonymisé pour la décision pure. */
    public record Candidate(int rating, long enqueuedAt) {}

    /** Résultat : les indices (dans la liste d'attente) à asseoir, et le nombre de bots à ajouter. */
    public record Formation(List<Integer> indices, int botsToAdd) {}

    private final RoomManager rooms;
    private final RatingService ratingService;
    private final EloConfig config;
    private final LongSupplier clock;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Deque<Waiting> waiting = new ArrayDeque<>();

    private record Waiting(WebSocketSession session, String name, long userId, int rating, long enqueuedAt) {}

    public RankedMatchmaker(RoomManager rooms, RatingService ratingService, EloConfig config) {
        this.rooms = rooms;
        this.ratingService = ratingService;
        this.config = config;
        this.clock = System::currentTimeMillis;
    }

    @PostConstruct
    void startTicker() {
        scheduler.scheduleAtFixedRate(this::tick, 1, 1, TimeUnit.SECONDS);
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }

    @Override
    public synchronized void enqueue(WebSocketSession session, String name, int desiredSize) {
        Long userId = session.get("userId", Long.class).orElse(null);
        if (userId == null) {
            return; // ranked exige un compte ; le WebSocket a déjà renvoyé l'erreur
        }
        session.put("mmRanked", true);
        waiting.add(new Waiting(session, name, userId, ratingService.ratingOf(userId), clock.getAsLong()));
        tryForm();
    }

    @Override
    public synchronized void cancel(WebSocketSession session) {
        waiting.removeIf(w -> w.session() == session);
    }

    @Override
    public synchronized int queuedCount() {
        return waiting.size();
    }

    private synchronized void tick() {
        tryForm();
    }

    private synchronized void tryForm() {
        if (waiting.isEmpty()) {
            return;
        }
        List<Waiting> snapshot = new ArrayList<>(waiting);
        List<Candidate> candidates = new ArrayList<>(snapshot.size());
        for (Waiting w : snapshot) {
            candidates.add(new Candidate(w.rating(), w.enqueuedAt()));
        }
        Optional<Formation> decision = decideFormation(candidates, clock.getAsLong(), config);
        if (decision.isEmpty()) {
            return;
        }
        Formation f = decision.get();
        List<Waiting> seated = new ArrayList<>();
        for (int idx : f.indices()) {
            seated.add(snapshot.get(idx));
        }
        waiting.removeAll(seated);

        GameRoom room = rooms.create(config.rankedTableSize(), Variants.DEVELOPER, "ranked");
        for (Waiting w : seated) {
            int seat = room.addHuman(w.session(), w.name(), w.userId());
            w.session().put("roomId", room.id());
            w.session().put("seat", seat);
        }
        for (int b = 0; b < f.botsToAdd(); b++) {
            room.addBot();
        }
        room.start(rooms.newSeed());
    }

    /** Fenêtre de rating acceptable pour un candidat selon son temps d'attente. */
    private static int window(Candidate c, long now, EloConfig cfg) {
        long steps = Math.max(0, (now - c.enqueuedAt()) / cfg.windowStepMs());
        return cfg.initialWindow() + (int) steps * cfg.windowWidthPerStep();
    }

    /**
     * Décide s'il faut former une table maintenant. D'abord une table 100% humaine dont l'écart de
     * rating tient dans la fenêtre commune ; sinon un bot-fill si une table faible-ELO a assez attendu.
     */
    public static Optional<Formation> decideFormation(List<Candidate> waiting, long now, EloConfig cfg) {
        int size = cfg.rankedTableSize();

        // 1) Table pleine d'humains : trier par rating, chercher un segment contigu de `size`
        //    dont l'écart (borne haute - borne basse) <= la plus petite fenêtre du segment.
        if (waiting.size() >= size) {
            List<Integer> byRating = new ArrayList<>();
            for (int i = 0; i < waiting.size(); i++) {
                byRating.add(i);
            }
            byRating.sort(Comparator.comparingInt(i -> waiting.get(i).rating()));
            for (int a = 0; a + size <= byRating.size(); a++) {
                int lo = waiting.get(byRating.get(a)).rating();
                int hi = waiting.get(byRating.get(a + size - 1)).rating();
                int spread = hi - lo;
                int minWindow = Integer.MAX_VALUE;
                List<Integer> group = new ArrayList<>(size);
                for (int k = 0; k < size; k++) {
                    int idx = byRating.get(a + k);
                    minWindow = Math.min(minWindow, window(waiting.get(idx), now, cfg));
                    group.add(idx);
                }
                if (spread <= minWindow) {
                    return Optional.of(new Formation(group, 0));
                }
            }
        }

        // 2) Bot-fill : table incomplète, faible-ELO, qui a dépassé le timeout d'attente.
        if (!waiting.isEmpty() && waiting.size() < size) {
            long oldestWait = now
                    - waiting.stream().mapToLong(Candidate::enqueuedAt).min().getAsLong();
            int maxRating = waiting.stream().mapToInt(Candidate::rating).max().getAsInt();
            if (oldestWait > cfg.rankedBotFillMs() && maxRating <= cfg.botFillMaxRating()) {
                List<Integer> all = new ArrayList<>();
                for (int i = 0; i < waiting.size(); i++) {
                    all.add(i);
                }
                return Optional.of(new Formation(all, size - waiting.size()));
            }
        }
        return Optional.empty();
    }
}
