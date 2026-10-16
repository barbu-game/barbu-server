package com.barbu.rami.match;

import com.barbu.rami.round.RamiRound;
import java.util.List;

public record RamiMatch(
        int playerCount,
        int totalRounds,
        long seed,
        int roundNumber,
        List<Integer> totals,
        RamiRound round,
        RoundSummary lastRound,
        List<RoundSummary> history) {

    public RamiMatch {
        history = history == null ? List.of() : List.copyOf(history);
    }

    public RamiMatch(
            int playerCount,
            int totalRounds,
            long seed,
            int roundNumber,
            List<Integer> totals,
            RamiRound round,
            RoundSummary lastRound) {
        this(playerCount, totalRounds, seed, roundNumber, totals, round, lastRound, List.of());
    }

    public boolean isComplete() {
        return round == null && roundNumber == totalRounds;
    }

    public int dealer() {
        return Math.floorMod(roundNumber - 1, playerCount);
    }
}
