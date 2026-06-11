package com.barbu.engine.model;

import static org.junit.jupiter.api.Assertions.*;

import com.barbu.engine.card.Card;
import com.barbu.engine.card.Rank;
import com.barbu.engine.card.Suit;
import org.junit.jupiter.api.Test;

class ModelTest {
    @Test
    void seats_rotate_within_player_count() {
        assertEquals(1, Seats.next(0, 4));
        assertEquals(0, Seats.next(3, 4));
        assertEquals(0, Seats.next(1, 2));
        assertEquals(2, Seats.MIN);
        assertEquals(10, Seats.MAX);
    }

    @Test
    void only_montante_is_a_montante_contract() {
        assertEquals(ContractType.MONTANTE, Contract.MONTANTE.type());
        assertEquals(ContractType.TRICK_TAKING, Contract.NO_TRICKS.type());
        assertEquals(ContractType.TRICK_TAKING, Contract.NO_RED_KINGS.type());
        assertEquals(9, Contract.values().length);
    }

    @Test
    void scoring_constants_match_the_spec_defaults() {
        assertEquals(-2, ScoringConfig.PER_TRICK);
        assertEquals(-2, ScoringConfig.PER_HEART);
        assertEquals(-6, ScoringConfig.PER_QUEEN);
        assertEquals(-10, ScoringConfig.PER_RED_KING);
    }

    @Test
    void montante_ranking_is_zero_sum_and_descending_for_any_n() {
        for (int n = 2; n <= 10; n++) {
            int[] ranking = ScoringConfig.montanteRanking(n);
            assertEquals(n, ranking.length, "n=" + n);
            assertEquals(0, java.util.Arrays.stream(ranking).sum(), "n=" + n + " not zero-sum");
            for (int p = 1; p < n; p++) {
                assertTrue(ranking[p] < ranking[p - 1], "n=" + n + " not strictly descending");
            }
        }
        assertArrayEquals(new int[] {15, 5, -5, -15}, ScoringConfig.montanteRanking(4));
    }

    @Test
    void move_is_sealed_play_or_pass() {
        Move play = new Move.PlayCard(new Card(Suit.SPADES, Rank.ACE));
        assertInstanceOf(Move.PlayCard.class, play);
        assertInstanceOf(Move.Pass.class, new Move.Pass());
        assertEquals(new Card(Suit.SPADES, Rank.ACE), ((Move.PlayCard) play).card());
    }
}
