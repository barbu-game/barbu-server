package com.barbu.engine.round;

import com.barbu.engine.card.Card;
import com.barbu.engine.model.Contract;
import com.barbu.engine.model.Move;
import java.util.ArrayList;
import java.util.List;

public final class RoundEngine {
    private RoundEngine() {}

    public static RoundState startTrickTaking(Contract contract, List<List<Card>> hands, int leader) {
        List<List<Card>> empty = new ArrayList<>();
        for (int i = 0; i < hands.size(); i++) {
            empty.add(List.of());
        }
        return new TrickTakingState(contract, hands, Trick.startedBy(leader, hands.size()), empty, List.of(), leader);
    }

    public static RoundState startMontante(List<List<Card>> hands, int opener) {
        return new MontanteState(hands, MontanteBoard.empty(), List.of(), 0, opener);
    }

    public static List<Move> legalMoves(RoundState state, int seat) {
        return switch (state) {
            case TrickTakingState t -> TrickTakingRules.legalMoves(t, seat);
            case MontanteState m -> MontanteRules.legalMoves(m, seat);
        };
    }

    public static RoundState applyMove(RoundState state, int seat, Move move) {
        return switch (state) {
            case TrickTakingState t -> TrickTakingRules.applyMove(t, seat, move);
            case MontanteState m -> MontanteRules.applyMove(m, seat, move);
        };
    }

    /** Clear a displayed, finished trick so its taker can lead the next one (trick-taking only). */
    public static RoundState collectTrick(RoundState state) {
        return state instanceof TrickTakingState t ? TrickTakingRules.collectTrick(t) : state;
    }
}
