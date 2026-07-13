package com.barbu.app.room;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Décision de matchmaking casual, pure et testable : forme une table pleine d'humains dès que
 * {@code size} joueurs attendent, sinon complète de bots une table incomplète passé le timeout.
 * Le wrapping (sessions, création de room) vit dans {@link MatchmakingCoordinator}.
 */
public final class CasualMatchmaker {

    private CasualMatchmaker() {}

    /** Un joueur en attente, anonymisé pour la décision. Les indices renvoyés référencent {@code waiting}. */
    public record Candidate(long enqueuedAt) {}

    public record Formation(List<Integer> indices, int botsToAdd) {}

    public static Optional<Formation> decideFormation(List<Candidate> waiting, long now, long fillTimeoutMs, int size) {
        if (waiting.isEmpty()) {
            return Optional.empty();
        }
        if (waiting.size() >= size) {
            return Optional.of(new Formation(range(size), 0));
        }
        long oldest =
                now - waiting.stream().mapToLong(Candidate::enqueuedAt).min().getAsLong();
        if (oldest >= fillTimeoutMs) {
            return Optional.of(new Formation(range(waiting.size()), size - waiting.size()));
        }
        return Optional.empty();
    }

    private static List<Integer> range(int n) {
        List<Integer> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(i);
        }
        return out;
    }
}
