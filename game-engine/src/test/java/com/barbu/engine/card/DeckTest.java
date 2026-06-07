package com.barbu.engine.card;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeckTest {
    @Test
    void full_deck_has_52_unique_cards() {
        List<Card> deck = Deck.full();
        assertEquals(52, deck.size());
        assertEquals(52, new HashSet<>(deck).size());
    }

    @Test
    void removal_order_starts_with_the_two_red_twos() {
        List<Card> order = Deck.removalOrder();
        assertEquals(new Card(Suit.HEARTS, Rank.TWO), order.get(0));
        assertEquals(new Card(Suit.DIAMONDS, Rank.TWO), order.get(1));
        assertEquals(new Card(Suit.CLUBS, Rank.TWO), order.get(2));
        assertEquals(new Card(Suit.SPADES, Rank.TWO), order.get(3));
    }

    @Test
    void reduced_deck_is_a_multiple_of_player_count() {
        for (int n = 2; n <= 10; n++) {
            List<Card> deck = Deck.reducedShuffled(n, 1L);
            assertEquals(0, deck.size() % n, "n=" + n);
            assertEquals(52 - (52 % n), deck.size(), "n=" + n);
            assertEquals(deck.size(), new HashSet<>(deck).size(), "n=" + n + " has duplicates");
        }
    }

    @Test
    void five_players_removes_exactly_the_two_red_twos() {
        List<Card> deck = Deck.reducedShuffled(5, 1L);
        assertFalse(deck.contains(new Card(Suit.HEARTS, Rank.TWO)));
        assertFalse(deck.contains(new Card(Suit.DIAMONDS, Rank.TWO)));
        assertTrue(deck.contains(new Card(Suit.CLUBS, Rank.TWO)));
        assertTrue(deck.contains(new Card(Suit.DIAMONDS, Rank.EIGHT)));
    }

    @Test
    void the_eight_of_diamonds_is_never_removed() {
        for (int n = 2; n <= 10; n++) {
            assertTrue(Deck.reducedShuffled(n, 7L).contains(new Card(Suit.DIAMONDS, Rank.EIGHT)), "n=" + n);
        }
    }

    @Test
    void shuffle_is_deterministic_for_a_seed() {
        assertEquals(Deck.reducedShuffled(4, 42L), Deck.reducedShuffled(4, 42L));
        assertNotEquals(Deck.reducedShuffled(4, 1L), Deck.reducedShuffled(4, 2L));
    }
}
