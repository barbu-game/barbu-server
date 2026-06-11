package com.barbu.engine.round;

import com.barbu.engine.card.Card;
import com.barbu.engine.model.Contract;
import java.util.ArrayList;
import java.util.List;

public record TrickTakingState(
        Contract contract,
        List<List<Card>> hands,
        Trick currentTrick,
        List<List<Card>> captured,
        List<Integer> trickTakers,
        int currentPlayer)
        implements RoundState {

    public TrickTakingState {
        hands = deepCopy(hands);
        captured = deepCopy(captured);
        trickTakers = List.copyOf(trickTakers);
    }

    static List<List<Card>> deepCopy(List<List<Card>> in) {
        List<List<Card>> out = new ArrayList<>(in.size());
        for (List<Card> hand : in) {
            out.add(List.copyOf(hand));
        }
        return List.copyOf(out);
    }

    @Override
    public int playerCount() {
        return hands.size();
    }

    @Override
    public boolean isComplete() {
        return hands.stream().allMatch(List::isEmpty);
    }
}
