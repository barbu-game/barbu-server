package com.barbu.bot.rami;

import static org.junit.jupiter.api.Assertions.*;

import com.barbu.engine.card.Card;
import com.barbu.engine.card.Rank;
import com.barbu.engine.card.Suit;
import com.barbu.rami.card.RamiCard;
import com.barbu.rami.card.RamiDeck;
import com.barbu.rami.meld.MeldRules;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.LongRange;
import org.junit.jupiter.api.Test;

class MeldFinderTest {
    private int nextId = 0;

    private RamiCard card(Suit suit, Rank rank) {
        return new RamiCard(nextId++, new Card(suit, rank));
    }

    private RamiCard joker() {
        return new RamiCard(nextId++, null);
    }

    @Test
    void finds_a_run_and_a_set() {
        List<RamiCard> hand = List.of(
                card(Suit.HEARTS, Rank.FIVE),
                card(Suit.HEARTS, Rank.SIX),
                card(Suit.HEARTS, Rank.SEVEN),
                card(Suit.HEARTS, Rank.EIGHT),
                card(Suit.SPADES, Rank.NINE),
                card(Suit.DIAMONDS, Rank.NINE),
                card(Suit.CLUBS, Rank.NINE),
                card(Suit.CLUBS, Rank.TWO));
        List<List<RamiCard>> melds = MeldFinder.find(hand);
        assertEquals(2, melds.size());
        assertEquals(4, melds.get(0).size());
        assertEquals(3, melds.get(1).size());
    }

    @Test
    void a_joker_completes_a_pair() {
        List<RamiCard> hand = List.of(card(Suit.SPADES, Rank.FOUR), card(Suit.SPADES, Rank.FIVE), joker());
        assertEquals(1, MeldFinder.find(hand).size());
    }

    @Test
    void ace_closes_a_high_run() {
        List<RamiCard> hand =
                List.of(card(Suit.CLUBS, Rank.QUEEN), card(Suit.CLUBS, Rank.KING), card(Suit.CLUBS, Rank.ACE));
        assertEquals(31, MeldRules.points(MeldFinder.find(hand).get(0)));
    }

    @Property(tries = 200)
    void found_melds_are_valid_and_disjoint(@ForAll @LongRange(min = 0, max = 100_000) long seed) {
        List<RamiCard> hand = RamiDeck.shuffled(seed).subList(0, 14);
        Set<Integer> seen = new HashSet<>();
        for (List<RamiCard> meld : MeldFinder.find(hand)) {
            assertTrue(MeldRules.kind(meld).isPresent(), meld.toString());
            for (RamiCard c : meld) {
                assertTrue(hand.contains(c));
                assertTrue(seen.add(c.id()));
            }
        }
    }
}
