package com.barbu.app.rating;

import java.util.ArrayList;
import java.util.List;

/**
 * Calcul ELO multi-joueurs par décomposition en paires : le classement final est traité
 * comme l'ensemble des duels deux à deux, chaque delta étant moyenné sur les N-1 adversaires.
 * Se réduit à l'ELO 1v1 standard à N=2. Pur : ne connaît ni la base, ni les rooms.
 */
public final class EloCalculator {

    private final EloConfig config;

    public EloCalculator(EloConfig config) {
        this.config = config;
    }

    /**
     * Un participant : son rating courant, son nombre de parties (pour le K provisoire), son rang
     * final (1 = premier).
     */
    public record Participant(int rating, int gamesPlayed, int placement) {}

    public record Delta(int ratingDelta) {}

    /** Renvoie un delta par participant, dans l'ordre de la liste d'entrée. */
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
