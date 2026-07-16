package com.barbu.app.room;

import com.barbu.app.rating.EloConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Ranked matchmaking decision, pure and testable: first a 100% human table whose rating spread fits
 * within a common window that widens with waiting time; otherwise a bot-fill bounded to low-ELO
 * tables that have waited long enough. The wrapping (sessions, rating, room creation) lives in
 * {@link MatchmakingCoordinator}.
 */
public final class RankedMatchmaker {

    private RankedMatchmaker() {}

    /** A waiting player, anonymized for the decision. The returned indices reference {@code waiting}. */
    public record Candidate(int rating, long enqueuedAt) {}

    /** Result: the indices (in the waiting list) to seat, and the number of bots to add. */
    public record Formation(List<Integer> indices, int botsToAdd) {}

    /** Acceptable rating window for a candidate based on its waiting time. */
    private static int window(Candidate c, long now, EloConfig cfg) {
        long steps = Math.max(0, (now - c.enqueuedAt()) / cfg.windowStepMs());
        return cfg.initialWindow() + (int) steps * cfg.windowWidthPerStep();
    }

    /**
     * Decides whether a table should be formed now. First a 100% human table whose rating spread
     * fits within the common window; otherwise a bot-fill if a low-ELO table has waited long enough.
     */
    public static Optional<Formation> decideFormation(List<Candidate> waiting, long now, EloConfig cfg) {
        int size = cfg.rankedTableSize();

        // 1) Full table of humans: sort by rating, look for a contiguous segment of `size`
        //    whose spread (upper bound - lower bound) <= the smallest window in the segment.
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

        // 2) Bot-fill: incomplete, low-ELO table that has exceeded the waiting timeout.
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
