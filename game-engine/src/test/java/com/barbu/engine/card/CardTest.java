package com.barbu.engine.card;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CardTest {
    @Test
    void detects_red_king() {
        assertTrue(new Card(Suit.HEARTS, Rank.KING).isRedKing());
        assertTrue(new Card(Suit.DIAMONDS, Rank.KING).isRedKing());
        assertFalse(new Card(Suit.SPADES, Rank.KING).isRedKing());
        assertFalse(new Card(Suit.HEARTS, Rank.QUEEN).isRedKing());
    }

    @Test
    void detects_queen_and_heart() {
        assertTrue(new Card(Suit.CLUBS, Rank.QUEEN).isQueen());
        assertTrue(new Card(Suit.HEARTS, Rank.TWO).isHeart());
        assertFalse(new Card(Suit.SPADES, Rank.TWO).isHeart());
    }

    @Test
    void value_equality() {
        assertEquals(new Card(Suit.SPADES, Rank.ACE), new Card(Suit.SPADES, Rank.ACE));
    }
}
