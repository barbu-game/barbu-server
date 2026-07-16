package com.barbu.app.room;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Casual matchmaking decision, pure and testable: forms a full table of humans as soon as
 * {@code size} players are waiting, otherwise fills an incomplete table with bots past the timeout.
 * The wrapping (sessions, room creation) lives in {@link MatchmakingCoordinator}.
 */
public final class CasualMatchmaker {

    private CasualMatchmaker() {}

    /** A waiting player, anonymized for the decision. The returned indices reference {@code waiting}. */
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
