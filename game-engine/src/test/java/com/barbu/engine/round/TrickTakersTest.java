package com.barbu.engine.round;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.barbu.engine.card.Card;
import com.barbu.engine.card.Rank;
import com.barbu.engine.card.Suit;
import com.barbu.engine.model.Contract;
import com.barbu.engine.model.Move;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrickTakersTest {
    @Test
    void takers_are_recorded_in_play_order() {
        List<List<Card>> hands = List.of(
                List.of(new Card(Suit.SPADES, Rank.KING), new Card(Suit.SPADES, Rank.TWO)),
                List.of(new Card(Suit.SPADES, Rank.QUEEN), new Card(Suit.SPADES, Rank.THREE)));
        RoundState state = RoundEngine.startTrickTaking(Contract.NO_TRICKS, hands, 0);
        state = RoundEngine.applyMove(state, 0, new Move.PlayCard(new Card(Suit.SPADES, Rank.KING)));
        state = RoundEngine.applyMove(state, 1, new Move.PlayCard(new Card(Suit.SPADES, Rank.QUEEN)));
        state = RoundEngine.collectTrick(state);
        state = RoundEngine.applyMove(state, 0, new Move.PlayCard(new Card(Suit.SPADES, Rank.TWO)));
        state = RoundEngine.applyMove(state, 1, new Move.PlayCard(new Card(Suit.SPADES, Rank.THREE)));
        assertEquals(List.of(0, 1), ((TrickTakingState) state).trickTakers());
    }
}
