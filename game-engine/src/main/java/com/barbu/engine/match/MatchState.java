package com.barbu.engine.match;

import com.barbu.engine.model.Contract;
import com.barbu.engine.round.RoundResult;
import com.barbu.engine.round.RoundState;
import com.barbu.engine.variant.Variant;
import java.util.List;
import java.util.Set;

public record MatchState(
        int playerCount,
        long seed,
        int dealer,
        int roundNumber,
        int plannedRounds,
        Set<Contract> playedByDealer,
        RoundState round,
        int[] totals,
        List<RoundResult> history,
        Variant variant) {
    public MatchState {
        playedByDealer = Set.copyOf(playedByDealer);
        totals = totals.clone();
        history = List.copyOf(history);
    }
}
