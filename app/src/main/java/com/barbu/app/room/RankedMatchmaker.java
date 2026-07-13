package com.barbu.app.room;

import com.barbu.app.rating.EloConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Décision de matchmaking ranked, pure et testable : d'abord une table 100% humaine dont l'écart de
 * rating tient dans une fenêtre commune qui s'élargit avec l'attente ; sinon un bot-fill borné aux
 * tables faible-ELO ayant assez patienté. Le wrapping (sessions, rating, création de room) vit dans
 * {@link MatchmakingCoordinator}.
 */
public final class RankedMatchmaker {

    private RankedMatchmaker() {}

    /** Un joueur en attente, anonymisé pour la décision. Les indices renvoyés référencent {@code waiting}. */
    public record Candidate(int rating, long enqueuedAt) {}

    /** Résultat : les indices (dans la liste d'attente) à asseoir, et le nombre de bots à ajouter. */
    public record Formation(List<Integer> indices, int botsToAdd) {}

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
