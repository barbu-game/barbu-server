package com.barbu.engine.round;

import com.barbu.engine.card.Card;
import com.barbu.engine.card.Rank;
import com.barbu.engine.card.Suit;
import com.barbu.engine.model.Contract;
import com.barbu.engine.model.Move;

import java.util.ArrayList;
import java.util.List;

public final class RoundEngine {
    private RoundEngine() {
    }

    public static RoundState startTrickTaking(Contract contract, List<List<Card>> hands, int leader) {
        List<List<Card>> empty = new ArrayList<>();
        for (int i = 0; i < hands.size(); i++) {
            empty.add(List.of());
        }
        return new TrickTakingState(contract, hands, Trick.startedBy(leader, hands.size()), empty, leader);
    }

    public static RoundState startMontante(List<List<Card>> hands, int eightOfDiamondsHolder) {
        return new MontanteState(hands, MontanteBoard.empty(), List.of(), 0, eightOfDiamondsHolder);
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

    public static RoundResult score(RoundState state) {
        return switch (state) {
            case TrickTakingState t -> RoundResult.fromMap(t.contract(), TrickTakingRules.score(t));
            case MontanteState m -> RoundResult.fromMap(m.contract(), MontanteRules.score(m));
        };
    }

    public static int eightOfDiamondsHolder(List<List<Card>> hands) {
        Card target = new Card(Suit.DIAMONDS, Rank.EIGHT);
        for (int seat = 0; seat < hands.size(); seat++) {
            if (hands.get(seat).contains(target)) {
                return seat;
            }
        }
        throw new IllegalArgumentException("8 of diamonds not dealt");
    }
}
