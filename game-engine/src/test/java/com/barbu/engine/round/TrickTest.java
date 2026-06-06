package com.barbu.engine.round;

import com.barbu.engine.card.Card;
import com.barbu.engine.card.Rank;
import com.barbu.engine.card.Suit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TrickTest {
    @Test
    void empty_trick_started_by_leader() {
        Trick t = Trick.startedBy(2, 4);
        assertEquals(2, t.leader());
        assertFalse(t.isComplete());
        assertNull(t.ledSuit());
    }

    @Test
    void completeness_depends_on_player_count() {
        Trick two = Trick.startedBy(0, 2)
                .withCard(new Card(Suit.HEARTS, Rank.FOUR))
                .withCard(new Card(Suit.HEARTS, Rank.KING));
        assertTrue(two.isComplete());

        Trick four = Trick.startedBy(0, 4)
                .withCard(new Card(Suit.HEARTS, Rank.FOUR))
                .withCard(new Card(Suit.HEARTS, Rank.KING));
        assertFalse(four.isComplete());
    }

    @Test
    void highest_of_led_suit_wins_offsuit_ignored() {
        Trick t = Trick.startedBy(1, 4)
                .withCard(new Card(Suit.HEARTS, Rank.FOUR))
                .withCard(new Card(Suit.HEARTS, Rank.KING))
                .withCard(new Card(Suit.SPADES, Rank.ACE))
                .withCard(new Card(Suit.HEARTS, Rank.NINE));
        assertTrue(t.isComplete());
        assertEquals(2, t.taker());
    }

    @Test
    void player_at_index_wraps_on_player_count() {
        Trick t = Trick.startedBy(2, 3);
        assertEquals(2, t.playerAt(0));
        assertEquals(0, t.playerAt(1));
        assertEquals(1, t.playerAt(2));
    }
}
