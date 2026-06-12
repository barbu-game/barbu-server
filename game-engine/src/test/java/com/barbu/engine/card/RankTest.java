package com.barbu.engine.card;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RankTest {
    @Test
    void ace_is_strongest_in_tricks() {
        assertTrue(Rank.ACE.trickStrength() > Rank.KING.trickStrength());
        assertEquals(14, Rank.ACE.trickStrength());
        assertEquals(2, Rank.TWO.trickStrength());
    }

    @Test
    void ace_is_high_in_montante() {
        assertEquals(14, Rank.ACE.montanteValue());
        assertTrue(Rank.ACE.montanteValue() > Rank.KING.montanteValue());
        assertEquals(8, Rank.EIGHT.montanteValue());
        assertEquals(13, Rank.KING.montanteValue());
    }

    @Test
    void there_are_thirteen_ranks() {
        assertEquals(13, Rank.values().length);
    }
}
