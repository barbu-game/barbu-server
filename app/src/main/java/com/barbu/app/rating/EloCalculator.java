package com.barbu.app.rating;

import java.util.ArrayList;
import java.util.List;

/**
 * Multi-player ELO computed by pairwise decomposition: the final ranking is treated as the set of
 * all head-to-head duels, each delta being averaged over the N-1 opponents. Reduces to standard 1v1
 * ELO at N=2. Pure: knows nothing about the database or the rooms.
 */
public final class EloCalculator {

    private final EloConfig config;

    public EloCalculator(EloConfig config) {
        this.config = config;
    }

    /**
     * A participant: current rating, number of games played (for the provisional K), and final
     * placement (1 = first).
     */
    public record Participant(int rating, int gamesPlayed, int placement) {}

    public record Delta(int ratingDelta) {}

    /** Returns one delta per participant, in the order of the input list. */
    public List<Delta> compute(List<Participant> participants) {
        int n = participants.size();
        List<Delta> out = new ArrayList<>(n);
        if (n < 2) {
            for (int i = 0; i < n; i++) {
                out.add(new Delta(0));
            }
            return out;
        }
        for (int i = 0; i < n; i++) {
            Participant p = participants.get(i);
            double sum = 0.0;
            for (int j = 0; j < n; j++) {
                if (i == j) {
                    continue;
                }
                Participant q = participants.get(j);
                double expected = 1.0 / (1.0 + Math.pow(10.0, (q.rating() - p.rating()) / 400.0));
                double actual = p.placement() < q.placement() ? 1.0 : (p.placement() > q.placement() ? 0.0 : 0.5);
                sum += actual - expected;
            }
            int k = p.gamesPlayed() < config.provisionalGames() ? config.provisionalKFactor() : config.kFactor();
            double delta = k * sum / (n - 1);
            out.add(new Delta((int) Math.round(delta)));
        }
        return out;
    }
}
