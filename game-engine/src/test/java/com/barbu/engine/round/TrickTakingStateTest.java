package com.barbu.engine.round;

import com.barbu.engine.card.Card;
import com.barbu.engine.card.Rank;
import com.barbu.engine.card.Suit;
import com.barbu.engine.model.Contract;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TrickTakingStateTest {
    @Test
    void player_count_is_derived_from_hands() {
        TrickTakingState s = state(threeHands(), 0);
        assertEquals(3, s.playerCount());
        assertEquals(Contract.NO_HEARTS, s.contract());
        assertEquals(0, s.currentPlayer());
    }

    @Test
    void complete_only_when_all_hands_empty() {
        assertFalse(state(threeHands(), 0).isComplete());
        TrickTakingState done = new TrickTakingState(
                Contract.NO_TRICKS,
                List.of(List.of(), List.of(), List.of()),
                Trick.startedBy(0, 3),
                List.of(List.of(), List.of(), List.of()), 0);
        assertTrue(done.isComplete());
    }

    @Test
    void defensively_copies_hands() {
        var mutable = new java.util.ArrayList<List<Card>>(List.of(
                new java.util.ArrayList<>(List.of(new Card(Suit.SPADES, Rank.TWO))),
                List.of(), List.of()));
        TrickTakingState s = new TrickTakingState(
                Contract.NO_TRICKS, mutable, Trick.startedBy(0, 3),
                List.of(List.of(), List.of(), List.of()), 0);
        mutable.get(0).clear();
        assertEquals(1, s.hands().get(0).size());
    }

    private static TrickTakingState state(List<List<Card>> hands, int current) {
        return new TrickTakingState(Contract.NO_HEARTS, hands, Trick.startedBy(current, hands.size()),
                List.of(List.of(), List.of(), List.of()), current);
    }

    private static List<List<Card>> threeHands() {
        return List.of(
                List.of(new Card(Suit.SPADES, Rank.TWO)),
                List.of(new Card(Suit.CLUBS, Rank.THREE)),
                List.of(new Card(Suit.HEARTS, Rank.FOUR)));
    }
}
