package com.barbu.engine.round;

import com.barbu.engine.card.Card;
import com.barbu.engine.model.Contract;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public record MontanteState(
        List<List<Card>> hands,
        MontanteBoard board,
        List<Integer> finishingOrder,
        int passStreak,
        int currentPlayer
) implements RoundState {

    public MontanteState {
        hands = TrickTakingState.deepCopy(hands);
        finishingOrder = List.copyOf(finishingOrder);
    }

    @Override
    public Contract contract() {
        return Contract.MONTANTE;
    }

    @Override
    public int playerCount() {
        return hands.size();
    }

    public int activeCount() {
        return playerCount() - finishingOrder.size();
    }

    @Override
    public boolean isComplete() {
        return finishingOrder.size() >= playerCount() - 1 || passStreak >= activeCount();
    }

    public boolean hasFinished(int seat) {
        return finishingOrder.contains(seat);
    }

    public List<Integer> finalRanking() {
        List<Integer> ranking = new ArrayList<>(finishingOrder);
        List<Integer> rest = new ArrayList<>();
        for (int seat = 0; seat < playerCount(); seat++) {
            if (!ranking.contains(seat)) {
                rest.add(seat);
            }
        }
        rest.sort(Comparator.comparingInt((Integer seat) -> hands.get(seat).size()).thenComparingInt(seat -> seat));
        ranking.addAll(rest);
        return List.copyOf(ranking);
    }
}
