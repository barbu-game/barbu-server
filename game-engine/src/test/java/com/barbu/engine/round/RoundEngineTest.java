package com.barbu.engine.round;

import static org.junit.jupiter.api.Assertions.*;

import com.barbu.engine.card.Card;
import com.barbu.engine.card.Rank;
import com.barbu.engine.card.Suit;
import com.barbu.engine.model.Contract;
import com.barbu.engine.model.Move;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoundEngineTest {
    private static Card c(Suit s, Rank r) {
        return new Card(s, r);
    }

    @Test
    void starts_a_trick_taking_round_with_given_leader() {
        List<List<Card>> hands = List.of(
                List.of(c(Suit.SPADES, Rank.TWO)),
                List.of(c(Suit.SPADES, Rank.THREE)),
                List.of(c(Suit.SPADES, Rank.FOUR)));
        RoundState s = RoundEngine.startTrickTaking(Contract.NO_TRICKS, hands, 2);
        assertEquals(2, s.currentPlayer());
        assertEquals(3, s.playerCount());
        assertEquals(Contract.NO_TRICKS, s.contract());
    }

    @Test
    void starts_montante_at_the_given_opener_seat() {
        List<List<Card>> hands = List.of(
                List.of(c(Suit.CLUBS, Rank.TWO)),
                List.of(c(Suit.DIAMONDS, Rank.EIGHT)),
                List.of(c(Suit.CLUBS, Rank.THREE)));
        RoundState s = RoundEngine.startMontante(hands, 2);
        assertEquals(2, s.currentPlayer());
        assertEquals(Contract.MONTANTE, s.contract());
    }

    @Test
    void dispatches_legal_moves_and_apply_by_type() {
        List<List<Card>> hands =
                List.of(List.of(c(Suit.HEARTS, Rank.TWO), c(Suit.SPADES, Rank.ACE)), List.of(), List.of());
        RoundState s = RoundEngine.startTrickTaking(Contract.NO_TRICKS, hands, 0);
        assertEquals(2, RoundEngine.legalMoves(s, 0).size());
        assertNotSame(s, RoundEngine.applyMove(s, 0, new Move.PlayCard(c(Suit.HEARTS, Rank.TWO))));
    }

    @Test
    void score_returns_round_result() {
        MontanteState done = new MontanteState(
                List.of(List.of(c(Suit.CLUBS, Rank.TWO)), List.of(), List.of(), List.of()),
                MontanteBoard.empty(),
                List.of(2, 0, 3),
                0,
                1);
        RoundResult r = RoundResult.fromMap(Contract.MONTANTE, MontanteRules.score(done));
        assertEquals(30, r.points()[2]);
        assertEquals(-30, r.points()[1]);
    }
}
