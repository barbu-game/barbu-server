package com.barbu.engine.card;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SuitTest {
    @Test
    void hearts_and_diamonds_are_red() {
        assertTrue(Suit.HEARTS.isRed());
        assertTrue(Suit.DIAMONDS.isRed());
    }

    @Test
    void clubs_and_spades_are_black() {
        assertFalse(Suit.CLUBS.isRed());
        assertFalse(Suit.SPADES.isRed());
    }

    @Test
    void there_are_four_suits() {
        assertEquals(4, Suit.values().length);
    }
}
