package com.barbu.engine.round;

import com.barbu.engine.card.Card;
import com.barbu.engine.card.Rank;
import com.barbu.engine.card.Suit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MontanteBoardTest {
    private static Card c(Suit s, Rank r) {
        return new Card(s, r);
    }

    @Test
    void empty_board_only_accepts_eights() {
        MontanteBoard b = MontanteBoard.empty();
        assertTrue(b.isPlayable(c(Suit.DIAMONDS, Rank.EIGHT)));
        assertTrue(b.isPlayable(c(Suit.SPADES, Rank.EIGHT)));
        assertFalse(b.isPlayable(c(Suit.DIAMONDS, Rank.SEVEN)));
        assertFalse(b.isPlayable(c(Suit.DIAMONDS, Rank.NINE)));
    }

    @Test
    void after_opening_extends_by_one_at_each_end() {
        MontanteBoard b = MontanteBoard.empty().place(c(Suit.HEARTS, Rank.EIGHT));
        assertTrue(b.isOpened(Suit.HEARTS));
        assertTrue(b.isPlayable(c(Suit.HEARTS, Rank.SEVEN)));
        assertTrue(b.isPlayable(c(Suit.HEARTS, Rank.NINE)));
        assertFalse(b.isPlayable(c(Suit.HEARTS, Rank.SIX)));
        assertFalse(b.isPlayable(c(Suit.HEARTS, Rank.TEN)));
        assertFalse(b.isPlayable(c(Suit.CLUBS, Rank.SEVEN)));
    }

    @Test
    void extends_down_to_ace_and_up_to_king() {
        MontanteBoard b = MontanteBoard.empty().place(c(Suit.SPADES, Rank.EIGHT));
        for (Rank r : new Rank[]{Rank.SEVEN, Rank.SIX, Rank.FIVE, Rank.FOUR, Rank.THREE, Rank.TWO, Rank.ACE}) {
            assertTrue(b.isPlayable(c(Suit.SPADES, r)), "down to " + r);
            b = b.place(c(Suit.SPADES, r));
        }
        assertFalse(b.isPlayable(c(Suit.SPADES, Rank.ACE)));
    }

    @Test
    void placing_non_playable_card_throws() {
        assertThrows(IllegalArgumentException.class, () -> MontanteBoard.empty().place(c(Suit.CLUBS, Rank.TWO)));
    }

    @Test
    void place_is_immutable() {
        MontanteBoard b = MontanteBoard.empty();
        b.place(c(Suit.HEARTS, Rank.EIGHT));
        assertFalse(b.isOpened(Suit.HEARTS));
    }
}
