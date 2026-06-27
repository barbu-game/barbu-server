package com.barbu.engine.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.barbu.engine.card.Card;
import com.barbu.engine.card.Rank;
import com.barbu.engine.card.Suit;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultMoveTest {

    @Test
    void picks_the_lowest_rank_card() {
        Move chosen = DefaultMove.pick(List.of(
                new Move.PlayCard(new Card(Suit.SPADES, Rank.FIVE)),
                new Move.PlayCard(new Card(Suit.HEARTS, Rank.TWO))));
        assertEquals(new Move.PlayCard(new Card(Suit.HEARTS, Rank.TWO)), chosen);
    }

    @Test
    void breaks_rank_ties_by_a_stable_suit_order_black_before_red() {
        Move chosen = DefaultMove.pick(List.of(
                new Move.PlayCard(new Card(Suit.DIAMONDS, Rank.TEN)),
                new Move.PlayCard(new Card(Suit.SPADES, Rank.TEN))));
        assertEquals(new Move.PlayCard(new Card(Suit.SPADES, Rank.TEN)), chosen);
    }

    @Test
    void plays_a_card_when_one_is_available_even_if_pass_is_legal() {
        Move chosen = DefaultMove.pick(List.of(new Move.Pass(), new Move.PlayCard(new Card(Suit.CLUBS, Rank.THREE))));
        assertEquals(new Move.PlayCard(new Card(Suit.CLUBS, Rank.THREE)), chosen);
    }

    @Test
    void passes_only_when_no_card_is_playable() {
        assertEquals(new Move.Pass(), DefaultMove.pick(List.of(new Move.Pass())));
    }
}
